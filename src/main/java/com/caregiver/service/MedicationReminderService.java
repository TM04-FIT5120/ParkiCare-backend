package com.caregiver.service;

import com.caregiver.entity.MedicationPlan;
import com.caregiver.repository.MedicationReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicationReminderService {

    private final MedicationReminderRepository medicationReminderRepository;

    public MedicationReminderService(MedicationReminderRepository medicationReminderRepository) {
        this.medicationReminderRepository = medicationReminderRepository;
    }

    @Transactional
    public MedicationPlan confirmReminder(Long remindId) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));

        plan.setRemindStatus(2); // 2 = completed / confirmed
        plan.setIsValid(0);      // completed, no longer pending
        plan.setSnoozeTime(null);

        return medicationReminderRepository.save(plan);
    }

    @Transactional
    public MedicationPlan snoozeReminder(Long remindId) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Reminder not found"));

        plan.setRemindStatus(3); // 3 = snoozed / later
        plan.setSnoozeTime(LocalDateTime.now().plusMinutes(5));

        return medicationReminderRepository.save(plan);
    }

    public List<MedicationPlan> getPendingReminders(Long patientId) {
        // 1 = 未处理, 3 = 稍后提醒
        return medicationReminderRepository.findByPatientIdAndRemindStatusIn(patientId, List.of(1, 3));
    }
}