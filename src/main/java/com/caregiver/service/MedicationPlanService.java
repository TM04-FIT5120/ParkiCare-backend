package com.caregiver.service;

import com.caregiver.entity.MedicationPlan;
import com.caregiver.repository.MedicationReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MedicationPlanService {

    private final MedicationReminderRepository medicationReminderRepository;

    public MedicationPlanService(MedicationReminderRepository medicationReminderRepository) {
        this.medicationReminderRepository = medicationReminderRepository;
    }

    @Transactional
    public MedicationPlan createMedicationPlan(Long patientId,
                                                     Long drugId,
                                                     String dosage,
                                                     String frequency,
                                                     LocalTime adminTimes,
                                                     LocalTime remindTime,
                                                     LocalDate startDate,
                                                     String planNote,
                                                     String mealTiming,
                                                     Integer quantity,
                                                     String intakeMethod,
                                                     LocalDate endDate,
                                                     String recurrence) {
        if (patientId == null) {
            throw new RuntimeException("Patient ID cannot be null");
        }
        if (drugId == null) {
            throw new RuntimeException("Drug ID cannot be null");
        }
        if (dosage == null || dosage.trim().isEmpty()) {
            throw new RuntimeException("Dosage cannot be empty");
        }
        if (frequency == null || frequency.trim().isEmpty()) {
            throw new RuntimeException("Frequency cannot be empty");
        }
        if (adminTimes == null) {
            throw new RuntimeException("At least one administration time is required");
        }
        if (startDate == null) {
            throw new RuntimeException("Start date cannot be null");
        }

        Long maxPlanId = medicationReminderRepository.findMaxPlanId();
        long nextPlanId = (maxPlanId == null) ? 1L : maxPlanId + 1L;

        MedicationPlan plan = new MedicationPlan();
        plan.setPlanId(nextPlanId);
        plan.setPatientId(patientId);
        plan.setDrugId(drugId);
        plan.setDosage(dosage.trim());
        plan.setFrequency(frequency.trim());
        plan.setAdminTime(adminTimes);
        plan.setRemindTime(remindTime);
        plan.setStartDate(startDate);
        plan.setIsOverdue(0);
        plan.setSnoozeTime(null);
        plan.setRemindStatus(0);
        plan.setIsValid(1);
        plan.setPlanNote(planNote);
        plan.setMealTiming(mealTiming);
        plan.setQuantity(quantity);
        plan.setIntakeMethod(intakeMethod);
        plan.setEndDate(endDate);
        plan.setRecurrence(recurrence);

        return medicationReminderRepository.save(plan);
    }

    public List<MedicationPlan> getPlansByPatient(Long patientId) {
        return medicationReminderRepository.findByPatientId(patientId);
    }

    public List<MedicationPlan> getPlansByPatientAndDate(Long patientId, LocalDate date) {
        return medicationReminderRepository.findByPatientIdAndStartDate(patientId, date);
    }
}