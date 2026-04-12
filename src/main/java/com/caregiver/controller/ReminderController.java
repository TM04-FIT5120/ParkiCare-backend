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
import java.util.List;

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

        // adminTimes may be comma-separated (e.g. "08:00,20:00") — use the first time only
        String firstAdminTime = request.getAdminTimes().split(",")[0].trim();

        return medicationPlanService.createMedicationPlan(
                request.getPatientId(),
                request.getDrugId(),
                request.getDosage(),
                request.getFrequency(),
                LocalTime.parse(firstAdminTime),
                LocalTime.parse(request.getRemindTime()),
                LocalDate.parse(request.getStartDate()),
                request.getPlanNote(),
                request.getMealTiming(),
                request.getQuantity(),
                request.getIntakeMethod(),
                endDate,
                request.getRecurrence()
        );
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
}