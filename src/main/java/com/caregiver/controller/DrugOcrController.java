package com.caregiver.controller;


import com.caregiver.dto.DrugOcrVO;
import com.caregiver.service.DrugOcrService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@CrossOrigin(origins = "*")
public class DrugOcrController {

    private final DrugOcrService drugOcrService;

    public DrugOcrController(DrugOcrService drugOcrService) {
        this.drugOcrService = drugOcrService;
    }

    @PostMapping("/drug")
    public DrugOcrVO ocrDrug(@RequestParam("file") MultipartFile file) throws Exception {
        return drugOcrService.recognizeDrug(file);
    }
}