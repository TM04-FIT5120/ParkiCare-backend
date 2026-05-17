package com.caregiver.controller;

import com.caregiver.dto.AutoMedicationPlanRequest;
import com.caregiver.dto.MedicationPlanRequest;
import com.caregiver.dto.MedicationPlanResponse;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.service.MedicationPlanService;
import com.caregiver.service.MedicationReminderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
    public MedicationPlanResponse createMedicationPlan(
            @Valid @RequestBody MedicationPlanRequest request) {

        LocalDate endDate = (request.getEndDate() != null && !request.getEndDate().isBlank())
                ? LocalDate.parse(request.getEndDate())
                : null;

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
                request.getAnchoredMeals(),
                request.getQuantity(),
                request.getIntakeMethod(),
                endDate,
                request.getRecurrence()
        );
        return convertToResponse(plans.get(0));
    }

    @GetMapping("/patient/{patientId}")
    public List<MedicationPlanResponse> getPlansByPatient(@PathVariable Long patientId) {
        return medicationPlanService.getPlansByPatient(patientId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    /** All medication plans covering today's calendar (is_valid 0/1). */
    @GetMapping("/patient/{patientId}/today-all")
    public List<MedicationPlanResponse> getTodayAllMedicationPlans(@PathVariable Long patientId) {
        return medicationPlanService.getTodayAllPlansByPatient(patientId, LocalDate.now()).stream()
                .map(this::convertToResponse)
                .toList();
    }

    @PatchMapping("/confirm/{remindId}")
    public MedicationPlanResponse confirmReminder(@PathVariable Long remindId,
                                                  @RequestParam Long caregiverId) {
        return convertToResponse(medicationReminderService.confirmReminder(remindId, caregiverId));
    }

    @PatchMapping("/later/{remindId}")
    public MedicationPlanResponse snoozeReminder(@PathVariable Long remindId,
                                                 @RequestParam Long caregiverId) {
        return convertToResponse(medicationReminderService.snoozeReminder(remindId, caregiverId));
    }

    @GetMapping("/pending/{patientId}")
    public List<MedicationPlanResponse> getPendingReminders(@PathVariable Long patientId,
                                                            @RequestParam Long caregiverId) {
        return medicationReminderService.getPendingReminders(patientId, caregiverId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    /**
     * Fallback for FCM Web Push: returns all pending/snoozed reminders for a caregiver
     * across all their patients. Called by the frontend when a push notification arrives
     * without data fields (FCM Web Push strips the data payload).
     */
    @GetMapping("/pending/caregiver/{caregiverId}")
    public List<MedicationPlanResponse> getPendingRemindersByCaregiver(@PathVariable Long caregiverId) {
        return medicationReminderService.getPendingRemindersByCaregiver(caregiverId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    @DeleteMapping("/{remindId}")
    public String deleteMedicationPlan(@PathVariable Long remindId,
                                       @RequestParam Long caregiverId) {
        medicationReminderService.deleteMedicationPlan(remindId, caregiverId);
        return "Medication plan deleted successfully";
    }

    private MedicationPlanResponse convertToResponse(MedicationPlan plan) {
        if (plan == null) return null;
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
    public List<MedicationPlanResponse> getMedicationPlansNow() {
        return medicationPlanService.getPlansRemindTimeNow().stream()
                .map(this::convertToResponse)
                .toList();
    }

    @GetMapping("/medicationPlan/plus5")
    public List<MedicationPlanResponse> getMedicationPlansPlus5() {
        return medicationPlanService.getPlansRemindTimePlus5().stream()
                .map(this::convertToResponse)
                .toList();
    }

    @PostMapping("/plan/auto")
    public List<MedicationPlanResponse> autoCreateMedicationPlan(
            @RequestBody AutoMedicationPlanRequest request) {
        return medicationPlanService.autoCreateMedicationPlan(request).stream()
                .map(this::convertToResponse)
                .toList();
    }
}
