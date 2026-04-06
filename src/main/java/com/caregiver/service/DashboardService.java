package com.caregiver.service;

import com.caregiver.dto.DashboardResponse;
import com.caregiver.dto.TaskResponse;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.entity.Patient;
import com.caregiver.repository.MedicationReminderRepository;
import com.caregiver.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final PatientRepository patientRepository;
    private final MedicationReminderRepository medicationReminderRepository;

    public DashboardService(PatientRepository patientRepository,
                            MedicationReminderRepository medicationReminderRepository) {
        this.patientRepository = patientRepository;
        this.medicationReminderRepository = medicationReminderRepository;
    }

    public DashboardResponse getDashboardSummary(Long caregiverId) {
        List<TaskResponse> todayTasks = getTodayTasks(caregiverId);
        List<TaskResponse> pendingReminders = getPendingReminders(caregiverId);
        List<TaskResponse> overdueTasks = getOverdueTasks(caregiverId);
        List<TaskResponse> upcomingTasks = getUpcomingTasks(caregiverId);

        boolean empty = todayTasks.isEmpty()
                && pendingReminders.isEmpty()
                && overdueTasks.isEmpty()
                && upcomingTasks.isEmpty();

        return new DashboardResponse(
                todayTasks,
                pendingReminders,
                overdueTasks,
                upcomingTasks,
                empty
        );
    }

    public List<TaskResponse> getTodayTasks(Long caregiverId) {
        List<MedicationPlan> plans = getMedicationPlansByCaregiver(caregiverId);
        LocalDate today = LocalDate.now();

        return plans.stream()
                .filter(plan -> plan.getStartDate() != null && plan.getStartDate().isEqual(today))
                .map(this::convertToTaskResponse)
                .sorted(Comparator.comparing(TaskResponse::getTime))
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getPendingReminders(Long caregiverId) {
        List<MedicationPlan> plans = getMedicationPlansByCaregiver(caregiverId);

        return plans.stream()
                .filter(plan -> plan.getIsValid() != null && plan.getIsValid() == 1)
                .filter(plan -> plan.getRemindStatus() != null &&
                        (plan.getRemindStatus() == 1 || plan.getRemindStatus() == 3))
                .map(this::convertToTaskResponse)
                .sorted(Comparator.comparing(TaskResponse::getTime))
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getOverdueTasks(Long caregiverId) {
        List<MedicationPlan> plans = getMedicationPlansByCaregiver(caregiverId);

        return plans.stream()
                .filter(plan -> plan.getIsOverdue() != null && plan.getIsOverdue() == 1)
                .filter(plan -> plan.getIsValid() != null && plan.getIsValid() == 1)
                .map(this::convertToTaskResponse)
                .sorted(Comparator.comparing(TaskResponse::getTime))
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getUpcomingTasks(Long caregiverId) {
        List<MedicationPlan> plans = getMedicationPlansByCaregiver(caregiverId);

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        return plans.stream()
                .filter(plan -> plan.getIsValid() != null && plan.getIsValid() == 1)
                .filter(plan -> plan.getStartDate() != null)
                .filter(plan ->
                        plan.getStartDate().isAfter(today) ||
                                (plan.getStartDate().isEqual(today)
                                        && plan.getAdminTime() != null
                                        && plan.getAdminTime().isAfter(now))
                )
                .map(this::convertToTaskResponse)
                .sorted(
                        Comparator.comparing(TaskResponse::getDate)
                                .thenComparing(TaskResponse::getTime)
                )
                .collect(Collectors.toList());
    }

    private List<MedicationPlan> getMedicationPlansByCaregiver(Long caregiverId) {
        List<Patient> patients = patientRepository.findByCaregiverId(caregiverId);

        if (patients.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> patientIds = patients.stream()
                .map(Patient::getId)
                .collect(Collectors.toList());

        return medicationReminderRepository.findByPatientIdIn(patientIds);
    }

    private TaskResponse convertToTaskResponse(MedicationPlan plan) {
        String status = convertStatus(plan);
        String priority = convertPriority(plan);

        return new TaskResponse(
                plan.getRemindId(),
                plan.getPatientId(),
                "Medication Reminder",
                "MEDICATION",
                plan.getStartDate() != null ? plan.getStartDate().toString() : null,
                plan.getAdminTime() != null ? plan.getAdminTime().toString() : null,
                status,
                priority,
                plan.getPlanNote()
        );
    }

    private String convertStatus(MedicationPlan plan) {
        if (plan.getRemindStatus() == null) {
            return "UNKNOWN";
        }

        return switch (plan.getRemindStatus()) {
            case 0 -> "NOT_TRIGGERED";
            case 1 -> "PENDING";
            case 2 -> "COMPLETED";
            case 3 -> "SNOOZED";
            default -> "UNKNOWN";
        };
    }

    private String convertPriority(MedicationPlan plan) {
        if (plan.getIsOverdue() != null && plan.getIsOverdue() == 1) {
            return "HIGH";
        }

        if (plan.getRemindStatus() != null && plan.getRemindStatus() == 1) {
            return "MEDIUM";
        }

        return "LOW";
    }
}