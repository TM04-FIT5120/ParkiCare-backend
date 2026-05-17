package com.caregiver.controller;

import com.caregiver.dto.DrugResponse;
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
    public List<DrugResponse> searchDrugs(@RequestParam String keyword) {
        return medicationReferenceService.searchDrugs(keyword).stream()
                .map(DrugResponse::from)
                .toList();
    }

    @GetMapping("/getAll")
    public List<DrugResponse> getAll() {
        return medicationReferenceService.getDrugs().stream()
                .map(DrugResponse::from)
                .toList();
    }

    @GetMapping("/{drugId:\\d+}")
    public DrugResponse getDrugById(@PathVariable Long drugId) {
        return DrugResponse.from(medicationReferenceService.getDrugById(drugId));
    }

    @GetMapping("/manufacturers")
    public List<String> searchManufacturers(@RequestParam String keyword) {
        return medicationReferenceService.searchManufacturers(keyword);
    }
}
