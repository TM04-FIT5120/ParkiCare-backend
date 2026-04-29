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
        if (request.getStartDate() == null || request.getStartDate().trim().isEmpty()) {
            throw new RuntimeException("Start date cannot be empty");
        }
        if (request.getEndDate() == null || request.getEndDate().trim().isEmpty()) {
            throw new RuntimeException("End date cannot be empty");
        }
        if (request.getDailyStartTime() == null || request.getDailyStartTime().trim().isEmpty()) {
            throw new RuntimeException("Daily start time cannot be empty");
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

        LocalDate startDate = LocalDate.parse(request.getStartDate());
        LocalDate endDate = LocalDate.parse(request.getEndDate());
        LocalTime dailyStartTime = LocalTime.parse(request.getDailyStartTime());

        if (endDate.isBefore(startDate)) {
            throw new RuntimeException("End date cannot be before start date");
        }

        String recurrence = request.getRecurrence();
        if (recurrence == null || recurrence.trim().isEmpty()) {
            recurrence = "daily";
        }

        Long maxPlanId = medicationReminderRepository.findMaxPlanId();
        long planId = (maxPlanId == null) ? 1L : maxPlanId + 1L;

        List<MedicationPlan> created = new ArrayList<>();

        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {

            for (int i = 0; i < request.getTimesPerDay(); i++) {

                LocalDateTime currentDateTime = LocalDateTime.of(currentDate, dailyStartTime)
                        .plusMinutes((long) i * drug.getIntervalMinutes());

                MedicationPlan plan = new MedicationPlan();

                plan.setPlanId(planId);
                plan.setPatientId(request.getPatientId());
                plan.setDrugId(request.getDrugId());

                plan.setDosage(dosage.trim());
                plan.setFrequency(frequency.trim());

                plan.setStartDate(currentDateTime.toLocalDate());
                plan.setAdminTime(currentDateTime.toLocalTime().withSecond(0).withNano(0));
                plan.setRemindTime(currentDateTime.toLocalTime().withSecond(0).withNano(0));

                plan.setIsOverdue(0);
                plan.setSnoozeTime(null);
                plan.setRemindStatus(0);
                plan.setIsValid(1);

                plan.setPlanNote(request.getPlanNote());
                plan.setMealTiming(request.getMealTiming());
                plan.setQuantity(request.getQuantity());
                plan.setIntakeMethod(request.getIntakeMethod());
                plan.setEndDate(endDate);
                plan.setRecurrence(recurrence);

                created.add(plan);
            }

            currentDate = currentDate.plusDays(1);
        }

        return medicationReminderRepository.saveAll(created);
    }

}