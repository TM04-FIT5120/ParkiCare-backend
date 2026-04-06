package com.caregiver.service;

import com.caregiver.entity.DrugBase;
import com.caregiver.repository.MedicationReferenceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MedicationReferenceService {

    private final MedicationReferenceRepository medicationReferenceRepository;

    public MedicationReferenceService(MedicationReferenceRepository medicationReferenceRepository) {
        this.medicationReferenceRepository = medicationReferenceRepository;
    }

    public List<DrugBase> searchDrugs(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        return medicationReferenceRepository.findByDrugNameContainingIgnoreCaseAndIsValid(keyword.trim(), 1);
    }

    public List<DrugBase> getDrugs() {
        return medicationReferenceRepository.findAll();
    }

    public DrugBase getDrugById(Long drugId) {
        return medicationReferenceRepository.findByDrugId(drugId)
                .orElseThrow(() -> new RuntimeException("Drug not found"));
    }
}