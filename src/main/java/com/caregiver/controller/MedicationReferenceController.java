package com.caregiver.controller;

import com.caregiver.entity.DrugBase;
import com.caregiver.service.MedicationReferenceService;
import org.springframework.web.bind.annotation.*;

        import java.util.List;

@RestController
@RequestMapping("/api/reference")
@CrossOrigin(origins = "*")
public class MedicationReferenceController {

    private final MedicationReferenceService medicationReferenceService;

    public MedicationReferenceController(MedicationReferenceService medicationReferenceService) {
        this.medicationReferenceService = medicationReferenceService;
    }

    @GetMapping("/search")
    public List<DrugBase> searchDrugs(@RequestParam String keyword) {
        return medicationReferenceService.searchDrugs(keyword);
    }

    @GetMapping("/{drugId}")
    public DrugBase getDrugById(@PathVariable Long drugId) {
        return medicationReferenceService.getDrugById(drugId);
    }
}