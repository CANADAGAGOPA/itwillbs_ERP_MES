package com.itwillbs.factron.dto.quality;

import lombok.Data;
import java.util.List;

@Data
public class RequestQualityInspectionUpdateDTO {
    private List<RequestQualityInspectionDTO> updateInspectionList;
} 