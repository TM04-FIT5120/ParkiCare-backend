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
    public List<MedicationPlan> createMedicationPlan(Long patientId,
                                                     Long drugId,
                                                     String dosage,
                                                     String frequency,
                                                     List<LocalTime> adminTimes,
                                                     LocalDate startDate,
                                                     String planNote) {
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
        if (adminTimes == null || adminTimes.isEmpty()) {
            throw new RuntimeException("At least one administration time is required");
        }
        if (startDate == null) {
            throw new RuntimeException("Start date cannot be null");
        }

        Long maxPlanId = medicationReminderRepository.findMaxPlanId();
        long nextPlanId = (maxPlanId == null) ? 1L : maxPlanId + 1L;

        List<MedicationPlan> result = new ArrayList<>();

        for (LocalTime adminTime : adminTimes) {
            MedicationPlan plan = new MedicationPlan();
            plan.setPlanId(nextPlanId);
            plan.setPatientId(patientId);
            plan.setDrugId(drugId);
            plan.setDosage(dosage.trim());
            plan.setFrequency(frequency.trim());
            plan.setAdminTime(adminTime);
            plan.setRemindTime(adminTime);
            plan.setStartDate(startDate);
            plan.setIsOverdue(0);
            plan.setSnoozeTime(null);
            plan.setRemindStatus(0); // 0 = NOT_TRIGGERED
            plan.setIsValid(1);      // 1 = valid / pending
            plan.setPlanNote(planNote);

            result.add(medicationReminderRepository.save(plan));
        }

        return result;
    }

    public List<MedicationPlan> getPlansByPatient(Long patientId) {
        return medicationReminderRepository.findByPatientId(patientId);
    }

    public List<MedicationPlan> getPlansByPatientAndDate(Long patientId, LocalDate date) {
        return medicationReminderRepository.findByPatientIdAndStartDate(patientId, date);
    }
}