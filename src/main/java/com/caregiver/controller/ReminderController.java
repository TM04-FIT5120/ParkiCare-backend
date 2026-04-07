package com.caregiver.controller;

import com.caregiver.dto.MedicationPlanRequest;
import com.caregiver.dto.MedicationPlanResponse;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.service.MedicationPlanService;
import com.caregiver.service.MedicationReminderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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

                MedicationPlan medicationPlan= medicationPlanService.createMedicationPlan(
                request.getPatientId(),
                request.getDrugId(),
                request.getDosage(),
                request.getFrequency(),
                LocalTime.parse(request.getAdminTimes()),
                LocalTime.parse(request.getRemindTime()),
                LocalDate.parse(request.getStartDate()),
                request.getPlanNote()
        );

        return medicationPlan;
    }

    @GetMapping("/patient/{patientId}")
    public List<MedicationPlan> getPlansByPatient(@PathVariable Long patientId) {
        return medicationPlanService.getPlansByPatient(patientId);
    }

    @PatchMapping("/confirm/{remindId}")
    public MedicationPlan confirmReminder(@PathVariable Long remindId) {
        return medicationReminderService.confirmReminder(remindId);
    }

    @PatchMapping("/later/{remindId}")
    public MedicationPlan snoozeReminder(@PathVariable Long remindId) {
        return medicationReminderService.snoozeReminder(remindId);
    }

    @GetMapping("/pending/{patientId}")
    public List<MedicationPlan> getPendingReminders(@PathVariable Long patientId) {
        return medicationReminderService.getPendingReminders(patientId);
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
                plan.getPlanNote()
        );
    }
}