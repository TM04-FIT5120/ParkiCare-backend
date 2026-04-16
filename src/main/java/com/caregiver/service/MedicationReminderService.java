package com.caregiver.service;

import com.caregiver.entity.MedicationPlan;
import com.caregiver.entity.Patient;
import com.caregiver.repository.MedicationReminderRepository;
import com.caregiver.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MedicationReminderService {

    private static final Logger log = LoggerFactory.getLogger(MedicationReminderService.class);

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
        // plan.setIsValid(0);      // completed, no longer pending
        // Keep isValid unchanged so popup confirm does not deactivate the row.
        // Scheduler will not re-fire this row because due query only picks remindStatus = 0.
        plan.setSnoozeTime(null);
        medicationReminderRepository.save(plan);

        // For recurring plans, create the next occurrence so the reminder fires again
        if (plan.getRecurrence() != null && !plan.getRecurrence().isBlank()) {
            LocalDate nextDate = nextOccurrenceDate(plan.getStartDate(), plan.getRecurrence());
            if (nextDate != null && (plan.getEndDate() == null || !nextDate.isAfter(plan.getEndDate()))) {
                MedicationPlan next = new MedicationPlan();
                next.setPlanId(plan.getPlanId());
                next.setPatientId(plan.getPatientId());
                next.setDrugId(plan.getDrugId());
                next.setDosage(plan.getDosage());
                next.setFrequency(plan.getFrequency());
                next.setAdminTime(plan.getAdminTime());
                next.setRemindTime(plan.getRemindTime());
                next.setStartDate(nextDate);
                next.setEndDate(plan.getEndDate());
                next.setRecurrence(plan.getRecurrence());
                next.setPlanNote(plan.getPlanNote());
                next.setMealTiming(plan.getMealTiming());
                next.setQuantity(plan.getQuantity());
                next.setIntakeMethod(plan.getIntakeMethod());
                next.setRemindStatus(0);
                next.setIsValid(1);
                next.setIsOverdue(0);
                medicationReminderRepository.save(next);
            }
        }

        return plan;
    }

    private LocalDate nextOccurrenceDate(LocalDate from, String recurrence) {
        return switch (recurrence.toLowerCase()) {
            case "daily" -> from.plusDays(1);
            case "weekdays" -> {
                // Advance by one day then keep skipping until we land on Mon–Fri.
                // getDayOfWeek().getValue() returns 6=Saturday, 7=Sunday.
                LocalDate next = from.plusDays(1);
                while (next.getDayOfWeek().getValue() >= 6) {
                    next = next.plusDays(1);
                }
                yield next;
            }
            case "weekly"  -> from.plusWeeks(1);
            case "monthly" -> from.plusMonths(1);
            default        -> null; // unknown recurrence pattern - do not auto-create
        };
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

    @Transactional
    public void deleteMedicationPlan(Long remindId, Long caregiverId) {
        MedicationPlan plan = medicationReminderRepository.findByRemindId(remindId)
                .orElseThrow(() -> new RuntimeException("Medication plan not found"));

        verifyPatientOwnership(plan.getPatientId(), caregiverId);

        plan.setIsValid(0); // soft-delete: mark as inactive
        medicationReminderRepository.save(plan);
    }

    public List<MedicationPlan> getPendingReminders(Long patientId, Long caregiverId) {
        verifyPatientOwnership(patientId, caregiverId);
        // 1 = pending, 3 = snoozed
        return medicationReminderRepository.findByPatientIdAndRemindStatusIn(patientId, List.of(1, 3));
    }

    /**
     * Return all pending/snoozed reminders across every patient belonging to a caregiver.
     * Used as a fallback when FCM Web Push strips the data payload - the frontend
     * calls this endpoint on notification arrival to recover remindId and caregiverId.
     */
    public List<MedicationPlan> getPendingRemindersByCaregiver(Long caregiverId) {
        List<MedicationPlan> results = medicationReminderRepository.findPendingByCaregiver(caregiverId);
        log.info("[Reminder] getPendingRemindersByCaregiver caregiverId={} - found {} result(s): {}",
                caregiverId,
                results.size(),
                results.stream().map(p -> "remindId=" + p.getRemindId() + " status=" + p.getRemindStatus()).toList());
        return results;
    }

    private void verifyPatientOwnership(Long patientId, Long caregiverId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!patient.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
    }
}
