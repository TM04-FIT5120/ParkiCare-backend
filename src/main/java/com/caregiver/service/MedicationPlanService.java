package com.caregiver.service;

import com.caregiver.entity.MedicationPlan;
import com.caregiver.repository.MedicationReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.caregiver.dto.AutoMedicationPlanRequest;
import com.caregiver.entity.DrugBase;
import com.caregiver.repository.DrugBaseRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MedicationPlanService {

    private final MedicationReminderRepository medicationReminderRepository;
    private final DrugBaseRepository drugBaseRepository;

    public MedicationPlanService(MedicationReminderRepository medicationReminderRepository, DrugBaseRepository drugBaseRepository) {
        this.medicationReminderRepository = medicationReminderRepository;
        this.drugBaseRepository = drugBaseRepository;
    }

    @Transactional
    public List<MedicationPlan> createMedicationPlan(Long patientId,
                                                     Long drugId,
                                                     String dosage,
                                                     String frequency,
                                                     List<LocalTime> adminTimes,
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
        if (adminTimes == null || adminTimes.isEmpty()) {
            throw new RuntimeException("At least one administration time is required");
        }
        if (startDate == null) {
            throw new RuntimeException("Start date cannot be null");
        }

        // All times for the same medication share one planId so they are grouped together.
        Long maxPlanId = medicationReminderRepository.findMaxPlanId();
        long nextPlanId = (maxPlanId == null) ? 1L : maxPlanId + 1L;

        List<MedicationPlan> created = new ArrayList<>();
        for (LocalTime adminTime : adminTimes) {
            MedicationPlan plan = new MedicationPlan();
            plan.setPlanId(nextPlanId);
            plan.setPatientId(patientId);
            plan.setDrugId(drugId);
            plan.setDosage(dosage.trim());
            plan.setFrequency(frequency.trim());
            plan.setAdminTime(adminTime);
            plan.setRemindTime(adminTime); // each row fires at its own time
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
            created.add(medicationReminderRepository.save(plan));
        }
        return created;
    }

    public List<MedicationPlan> getPlansByPatient(Long patientId) {
        return medicationReminderRepository.findByPatientIdAndIsValid(patientId, 1);
    }

    public List<MedicationPlan> getPlansByPatientAndDate(Long patientId, LocalDate date) {
        return medicationReminderRepository.findByPatientIdAndStartDate(patientId, date);
    }

    public List<MedicationPlan> getPlansRemindTimeNow() {
        LocalDate targetDate = LocalDate.now();
        LocalTime targetTime = LocalTime.now().withSecond(0).withNano(0);
        return medicationReminderRepository.findActiveByRemindTimeOnDate(targetTime, targetDate);
    }

    public List<MedicationPlan> getPlansRemindTimePlus5() {
        LocalDate nowDate = LocalDate.now();
        LocalTime nowTime = LocalTime.now().withSecond(0).withNano(0);

        LocalTime plus5 = nowTime.plusMinutes(5);

        // 注意：LocalTime 可能跨日（23:58 +5 = 00:03）
        LocalDate targetDate = plus5.isBefore(nowTime) ? nowDate.plusDays(1) : nowDate;

        return medicationReminderRepository.findActiveByRemindTimeOnDate(plus5, targetDate);
    }

    @Transactional
    public List<MedicationPlan> autoCreateMedicationPlan(AutoMedicationPlanRequest request) {

        if (request.getPatientId() == null) {
            throw new RuntimeException("Patient ID cannot be null");
        }

        if (request.getDrugId() == null) {
            throw new RuntimeException("Drug ID cannot be null");
        }

        if (request.getStartDateTime() == null || request.getStartDateTime().trim().isEmpty()) {
            throw new RuntimeException("Start datetime cannot be empty");
        }

        if (request.getTimesPerDay() == null || request.getTimesPerDay() <= 0) {
            throw new RuntimeException("Times per day must be greater than 0");
        }

        DrugBase drug = drugBaseRepository.findById(request.getDrugId())
                .orElseThrow(() -> new RuntimeException("Drug not found"));

        if (drug.getIntervalMinutes() == null || drug.getIntervalMinutes() <= 0) {
            throw new RuntimeException("Drug interval minutes is not configured");
        }

        String dosage = request.getDosage();
        if (dosage == null || dosage.trim().isEmpty()) {
            dosage = drug.getDosage();
        }
        if (dosage == null || dosage.trim().isEmpty()) {
            throw new RuntimeException("Dosage cannot be empty");
        }

        String frequency = request.getFrequency();
        if (frequency == null || frequency.trim().isEmpty()) {
            frequency = drug.getFrequency();
        }
        if (frequency == null || frequency.trim().isEmpty()) {
            frequency = request.getTimesPerDay() + " times per day";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startDateTime = LocalDateTime.parse(request.getStartDateTime(), formatter);

        List<LocalTime> adminTimes = new ArrayList<>();

        for (int i = 0; i < request.getTimesPerDay(); i++) {
            LocalDateTime current = startDateTime.plusMinutes(
                    (long) i * drug.getIntervalMinutes()
            );

            adminTimes.add(
                    current.toLocalTime().withSecond(0).withNano(0)
            );
        }

        LocalDate endDate = null;
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            endDate = LocalDate.parse(request.getEndDate());
        }

        String recurrence = request.getRecurrence();
        if (recurrence == null || recurrence.trim().isEmpty()) {
            recurrence = "none";
        }

        return createMedicationPlan(
                request.getPatientId(),
                request.getDrugId(),
                dosage,
                frequency,
                adminTimes,
                startDateTime.toLocalDate(),
                request.getPlanNote(),
                request.getMealTiming(),
                request.getQuantity(),
                request.getIntakeMethod(),
                endDate,
                recurrence
        );
    }

}