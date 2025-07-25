package com.itwillbs.factron.service.approval;

import com.itwillbs.factron.dto.approval.RequestApprovalDTO;
import com.itwillbs.factron.dto.approval.ResponseSearchApprovalDTO;
import com.itwillbs.factron.dto.approval.RequestSearchApprovalDTO;
import com.itwillbs.factron.entity.Approval;
import com.itwillbs.factron.entity.Employee;
import com.itwillbs.factron.entity.IntergratAuth;
import com.itwillbs.factron.entity.Transfer;
import com.itwillbs.factron.mapper.approval.ApprovalMapper;
import com.itwillbs.factron.repository.approval.ApprovalRepository;
import com.itwillbs.factron.repository.employee.EmployeeRepository;
import com.itwillbs.factron.repository.employee.IntergratAuthRepository;
import com.itwillbs.factron.repository.transfer.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRepository approvalRespository;            // 결재 엔티티 저장소
    private final ApprovalMapper approvalMapper;                     // 결재 조회용 Mapper (MyBatis 또는 유사 Mapper)
    private final EmployeeRepository employeeRepository;             // 직원 정보 저장소
    private final TransferRepository transferRepository;             // 인사 발령 정보 저장소
    private final IntergratAuthRepository intergratAuthRepository;   // 통합 권한 정보 저장소

    // 인사결재 전체 조회
    @Override
    public List<ResponseSearchApprovalDTO> getApprovalsList(RequestSearchApprovalDTO requestSearchApprovalDTO){
        return approvalMapper.getApprovalList(requestSearchApprovalDTO);
    }

    // 인사결재 단일 조회
    @Override
    public ResponseSearchApprovalDTO getApprovalById(Long approvalId) {
        return approvalMapper.selectApprovalById(approvalId);
    }


    // 인사결재 (승인, 반려)
    @Override
    @Transactional // 쓰기 작업이므로 트랜잭션 설정
    public void updateApproval(RequestApprovalDTO requestApprovalDTO) {
        // 1. 결재 내역 조회
        Approval approval = approvalRespository.findById(requestApprovalDTO.getApprovalId())
                .orElseThrow(() -> new IllegalArgumentException("해당 결재 내역을 찾을 수 없습니다."));

        // 2. 결재자(승인자) 조회
        Employee approver = employeeRepository.findById(requestApprovalDTO.getApproverId())
                .orElseThrow(() -> new IllegalArgumentException("결재자를 찾을 수 없습니다."));

        // 3. 결재 승인 처리
        if ("APV002".equals(requestApprovalDTO.getApprovalStatus())) {
            approval.approve(approver); // 승인 처리 (결재 엔티티 내 메서드 호출)

            // 추가 처리: 인사 발령(APR003)일 경우
            if ("APR003".equals(requestApprovalDTO.getApprovalType())) {

                // 3-1. 해당 결재의 발령 정보 조회
                Transfer transfer = transferRepository.findByApprovalId(approval.getId())
                        .orElseThrow(() -> new IllegalArgumentException("해당 결재에 대한 발령 정보가 없습니다."));

                // 3-2. 발령 대상 직원 조회
                Employee targetEmp = transfer.getEmployee();

                // 3-3. 발령 유형에 따라 직급/부서 변경
                switch (transfer.getTransferTypeCode()) {
                    case "TRS001": // 승진
                        targetEmp.updatePositionCode(transfer.getPositionCode());
                        break;

                    case "TRS002": // 전보
                        targetEmp.updateDeptCode(transfer.getCurrDeptCode());
                        break;
                }

                // 3-4. 권한 변경 처리
                IntergratAuth auth = intergratAuthRepository.findByEmployee(targetEmp)
                        .orElseThrow(() -> new IllegalArgumentException("해당 직원의 로그인 정보가 없습니다."));

                String newAuthCode = switch (targetEmp.getDeptCode()) {
                    case "DEP001" -> "ATH002";           // 인사
                    case "DEP002" -> {
                        if ("POS006".equals(targetEmp.getPositionCode()) || "POS007".equals(targetEmp.getPositionCode()))
                            yield "ATH003";              // 관리자
                        else
                            yield "ATH001";              // 일반
                    }
                    case "DEP003" -> "ATH004";           // 영업
                    case "DEP005" -> "ATH005";           // 재무
                    case "DEP006" -> {
                        if ("POS005".equals(targetEmp.getPositionCode()))
                            yield "ATH007";              // 작업반장(생산팀에 부장급이상)
                        else
                            yield "ATH006";              // 생산사원
                    }
                    default -> "ATH001";                 // 기본 일반
                };

                auth.updateAuthCode(newAuthCode);

                // 3-5. 발령일 업데이트
                transfer.updateTransferDate(LocalDate.now());
            }

        }
        // 4. 결재 반려 처리
        else if ("APV003".equals(requestApprovalDTO.getApprovalStatus())) {
            approval.reject(approver, requestApprovalDTO.getRejectionReason());
        }
        // 5. 유효하지 않은 상태 처리
        else {
            throw new IllegalArgumentException("유효하지 않은 결재 상태 코드입니다.");
        }
        // ※ JPA 변경감지로 자동 저장 (별도 save 호출 불필요)
    }
}
