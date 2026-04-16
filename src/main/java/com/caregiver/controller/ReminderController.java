package com.caregiver.controller;

import com.caregiver.dto.MedicationPlanRequest;
import com.caregiver.dto.MedicationPlanResponse;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.service.MedicationPlanService;
import com.caregiver.service.MedicationReminderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reminder")
@CrossOrigin(origins = "*")
public class ReminderController {

    private final MedicationPlanService medicationPlanService;
    private final MedicationReminderService medicationReminderService;

    public ReminderController(MedicationPlanService medicationPlanService,
                              MedicationReminderService medicationReminderService) {
        this.medicationPlanService = medicationPlanService;
        this.medicationReminderService = medicationReminderService;
    }

    @PostMapping("/plan")
    public MedicationPlan createMedicationPlan(
            @Valid @RequestBody MedicationPlanRequest request) {

        LocalDate endDate = (request.getEndDate() != null && !request.getEndDate().isBlank())
                ? LocalDate.parse(request.getEndDate())
                : null;

        // Parse every comma-separated time and create one MedicationPlan row per time,
        // all sharing the same planId so they are treated as one medication group.
        List<LocalTime> adminTimes = Arrays.stream(request.getAdminTimes().split(","))
                .map(String::trim)
                .map(LocalTime::parse)
                .collect(Collectors.toList());

        List<MedicationPlan> plans = medicationPlanService.createMedicationPlan(
                request.getPatientId(),
                request.getDrugId(),
                request.getDosage(),
                request.getFrequency(),
                adminTimes,
                LocalDate.parse(request.getStartDate()),
                request.getPlanNote(),
                request.getMealTiming(),
                request.getQuantity(),
                request.getIntakeMethod(),
                endDate,
                request.getRecurrence()
        );
        // Return the first plan for frontend compatibility; all plans are persisted.
        return plans.get(0);
    }

    @GetMapping("/patient/{patientId}")
    public List<MedicationPlan> getPlansByPatient(@PathVariable Long patientId) {
        return medicationPlanService.getPlansByPatient(patientId);
    }

    @PatchMapping("/confirm/{remindId}")
    public MedicationPlan confirmReminder(@PathVariable Long remindId,
                                          @RequestParam Long caregiverId) {
        return medicationReminderService.confirmReminder(remindId, caregiverId);
    }

    @PatchMapping("/later/{remindId}")
    public MedicationPlan snoozeReminder(@PathVariable Long remindId,
                                         @RequestParam Long caregiverId) {
        return medicationReminderService.snoozeReminder(remindId, caregiverId);
    }

    @GetMapping("/pending/{patientId}")
    public List<MedicationPlan> getPendingReminders(@PathVariable Long patientId,
                                                    @RequestParam Long caregiverId) {
        return medicationReminderService.getPendingReminders(patientId, caregiverId);
    }

    /**
     * Fallback for FCM Web Push: returns all pending/snoozed reminders for a caregiver
     * across all their patients. Called by the frontend when a push notification arrives
     * without data fields (FCM Web Push strips the data payload).
     */
    @GetMapping("/pending/caregiver/{caregiverId}")
    public List<MedicationPlan> getPendingRemindersByCaregiver(@PathVariable Long caregiverId) {
        return medicationReminderService.getPendingRemindersByCaregiver(caregiverId);
    }

    @DeleteMapping("/{remindId}")
    public String deleteMedicationPlan(@PathVariable Long remindId,
                                       @RequestParam Long caregiverId) {
        medicationReminderService.deleteMedicationPlan(remindId, caregiverId);
        return "Medication plan deleted successfully";
    }

    private MedicationPlanResponse convertToResponse(MedicationPlan plan) {
        return new MedicationPlanResponse(
                plan.getRemindId(),
                plan.getPatientId(),
                plan.getDrugId(),
                plan.getDosage(),
                plan.getFrequency(),
                plan.getStartDate() != null ? plan.getStartDate().toString() : null,
                plan.getAdminTime() != null ? plan.getAdminTime().toString() : null,
                String.valueOf(plan.getRemindStatus()),
                plan.getPlanNote(),
                plan.getMealTiming(),
                plan.getQuantity(),
                plan.getIntakeMethod(),
                plan.getEndDate() != null ? plan.getEndDate().toString() : null,
                plan.getRecurrence()
        );
    }

    @GetMapping("/medicationPlan/now")
    public List<MedicationPlan> getMedicationPlansNow() {
        return medicationPlanService.getPlansRemindTimeNow();
    }

    @GetMapping("/medicationPlan/plus5")
    public List<MedicationPlan> getMedicationPlansPlus5() {
        return medicationPlanService.getPlansRemindTimePlus5();
    }

}