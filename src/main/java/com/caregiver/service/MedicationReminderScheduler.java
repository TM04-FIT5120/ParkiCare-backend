package com.caregiver.service;

import com.caregiver.entity.MedicationPlan;
import com.caregiver.entity.Patient;
import com.caregiver.repository.MedicationReminderRepository;
import com.caregiver.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class MedicationReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(MedicationReminderScheduler.class);

    private final MedicationReminderRepository medicationReminderRepository;
    private final PatientRepository patientRepository;
    private final PushNotificationService pushNotificationService;

    public MedicationReminderScheduler(MedicationReminderRepository medicationReminderRepository,
                                       PatientRepository patientRepository,
                                       PushNotificationService pushNotificationService) {
        this.medicationReminderRepository = medicationReminderRepository;
        this.patientRepository = patientRepository;
        this.pushNotificationService = pushNotificationService;
    }

    /**
     * Runs every minute. Finds medication plans whose remindTime falls within the
     * last 60 seconds and fires a push notification to the caregiver.
     * Also re-fires notifications for snoozed reminders whose snooze period has elapsed.
     *
     * The application timezone is set to Asia/Kuala_Lumpur (MYT) at startup,
     * so LocalTime.now() already reflects MYT.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void triggerDueReminders() {
        LocalTime now = LocalTime.now();
        LocalTime oneMinuteAgo = now.minusMinutes(1);
        LocalDate today = LocalDate.now();

        log.info("[Scheduler] Tick at {} — scanning window [{}, {}] for date {}", now, oneMinuteAgo, now, today);

        // --- 1. Newly due reminders (remindStatus = 0) ---
        List<MedicationPlan> dueReminders = medicationReminderRepository
                .findDueReminders(oneMinuteAgo, now, today);

        log.info("[Scheduler] Found {} due reminder(s)", dueReminders.size());

        for (MedicationPlan plan : dueReminders) {
            Long caregiverId = getCaregiverId(plan.getPatientId());
            if (caregiverId == null) {
                log.warn("[Scheduler] No caregiver found for patientId={}, skipping remindId={}", plan.getPatientId(), plan.getRemindId());
                continue;
            }

            log.info("[Scheduler] Sending notification for remindId={} to caregiverId={}", plan.getRemindId(), caregiverId);
            String body = buildNotificationBody(plan);
            pushNotificationService.sendToCaregiver(caregiverId, "Medication Reminder", body);

            plan.setRemindStatus(1); // pending - awaiting confirmation
            medicationReminderRepository.save(plan);
        }

        // --- 2. Snoozed reminders whose snooze time has elapsed ---
        List<MedicationPlan> snoozedReminders = medicationReminderRepository
                .findSnoozedRemindersReady(LocalDateTime.now());

        if (!snoozedReminders.isEmpty()) {
            log.info("[Scheduler] Found {} snoozed reminder(s) ready to re-fire", snoozedReminders.size());
        }

        for (MedicationPlan plan : snoozedReminders) {
            Long caregiverId = getCaregiverId(plan.getPatientId());
            if (caregiverId == null) {
                log.warn("[Scheduler] No caregiver found for patientId={}, skipping snoozed remindId={}", plan.getPatientId(), plan.getRemindId());
                continue;
            }

            log.info("[Scheduler] Re-firing snoozed remindId={} to caregiverId={}", plan.getRemindId(), caregiverId);
            String body = buildNotificationBody(plan);
            pushNotificationService.sendToCaregiver(caregiverId, "Medication Reminder (Snoozed)", body);

            plan.setRemindStatus(1); // back to pending
            plan.setSnoozeTime(null);
            medicationReminderRepository.save(plan);
        }
    }

    private Long getCaregiverId(Long patientId) {
        return patientRepository.findById(patientId)
                .map(Patient::getCaregiverId)
                .orElse(null);
    }

    private String buildNotificationBody(MedicationPlan plan) {
        StringBuilder sb = new StringBuilder("Time to take your medication");
        if (plan.getDosage() != null && !plan.getDosage().isBlank()) {
            sb.append(" – ").append(plan.getDosage());
        }
        if (plan.getMealTiming() != null && !plan.getMealTiming().isBlank()) {
            sb.append(" (").append(plan.getMealTiming()).append(")");
        }
        return sb.toString();
    }
}
