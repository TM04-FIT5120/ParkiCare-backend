package com.caregiver.service;

import com.caregiver.entity.Caregiver;
import com.caregiver.repository.CaregiverRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class CaregiverService {

    private final CaregiverRepository caregiverRepository;

    public CaregiverService(CaregiverRepository caregiverRepository) {
        this.caregiverRepository = caregiverRepository;
    }

    @Transactional
    public void updateLanguage(Long id, String language) {
        Caregiver caregiver = caregiverRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Caregiver not found"));
        caregiver.setLanguage(language);
        caregiverRepository.save(caregiver);
    }
}
