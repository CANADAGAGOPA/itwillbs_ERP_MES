package com.itwillbs.factron.controller.storage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/storage")
public class StorageController {

    @GetMapping("")
    public String storage() { return "storage/storage"; }
}
