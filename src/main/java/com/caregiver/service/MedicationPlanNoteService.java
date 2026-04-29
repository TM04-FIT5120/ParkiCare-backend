package com.caregiver.service;

import com.caregiver.dto.MedicationPlanNoteResponse;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.entity.Patient;
import com.caregiver.repository.MedicationReminderRepository;
import com.caregiver.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicationPlanNoteService {

    private final MedicationReminderRepository medicationReminderRepository;
    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public MedicationPlanNoteResponse getPlanNote(Long remindId, Long caregiverId) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));
        verifyPatientOwnership(plan.getPatientId(), caregiverId);

        return new MedicationPlanNoteResponse(plan.getRemindId(), plan.getPlanNote());
    }

    @Transactional
    public MedicationPlanNoteResponse createOrUpdatePlanNote(Long remindId, Long caregiverId, String planNote) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));
        verifyPatientOwnership(plan.getPatientId(), caregiverId);

        plan.setPlanNote(planNote.trim());
        medicationReminderRepository.save(plan);

        return new MedicationPlanNoteResponse(plan.getRemindId(), plan.getPlanNote());
    }

    @Transactional
    public MedicationPlanNoteResponse deletePlanNote(Long remindId, Long caregiverId) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));
        verifyPatientOwnership(plan.getPatientId(), caregiverId);

        plan.setPlanNote(null);
        medicationReminderRepository.save(plan);

        return new MedicationPlanNoteResponse(plan.getRemindId(), null);
    }

    private void verifyPatientOwnership(Long patientId, Long caregiverId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!patient.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
    }
}

