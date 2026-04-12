package com.caregiver.service;

import com.caregiver.entity.MedicationPlan;
import com.caregiver.entity.Patient;
import com.caregiver.repository.MedicationReminderRepository;
import com.caregiver.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicationReminderService {

    private final MedicationReminderRepository medicationReminderRepository;
    private final PatientRepository patientRepository;

    public MedicationReminderService(MedicationReminderRepository medicationReminderRepository,
                                     PatientRepository patientRepository) {
        this.medicationReminderRepository = medicationReminderRepository;
        this.patientRepository = patientRepository;
    }

    @Transactional
    public MedicationPlan confirmReminder(Long remindId, Long caregiverId) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));

        verifyPatientOwnership(plan.getPatientId(), caregiverId);

        plan.setRemindStatus(2); // 2 = completed / confirmed
        plan.setIsValid(0);      // completed, no longer pending
        plan.setSnoozeTime(null);

        return medicationReminderRepository.save(plan);
    }

    @Transactional
    public MedicationPlan snoozeReminder(Long remindId, Long caregiverId) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));

        verifyPatientOwnership(plan.getPatientId(), caregiverId);

        plan.setRemindStatus(3); // 3 = snoozed / later
        plan.setSnoozeTime(LocalDateTime.now().plusMinutes(5));

        return medicationReminderRepository.save(plan);
    }

    public List<MedicationPlan> getPendingReminders(Long patientId, Long caregiverId) {
        verifyPatientOwnership(patientId, caregiverId);
        // 1 = pending, 3 = snoozed
        return medicationReminderRepository.findByPatientIdAndRemindStatusIn(patientId, List.of(1, 3));
    }

    private void verifyPatientOwnership(Long patientId, Long caregiverId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!patient.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
    }
}
