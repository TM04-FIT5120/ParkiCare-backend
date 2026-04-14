package com.caregiver.repository;

import com.caregiver.entity.MedicationPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface MedicationReminderRepository extends JpaRepository<MedicationPlan, Long> {

    Optional<MedicationPlan> findByRemindId(Long remindId);

    List<MedicationPlan> findByPatientId(Long patientId);

    List<MedicationPlan> findByPatientIdAndIsValid(Long patientId, Integer isValid);

    List<MedicationPlan> findByPatientIdAndStartDate(Long patientId, LocalDate startDate);

    List<MedicationPlan> findByPatientIdAndRemindStatusIn(Long patientId, List<Integer> statuses);

    List<MedicationPlan> findByPatientIdIn(List<Long> patientIds);

    @Query(value = "SELECT MAX(plan_id) FROM medication_plan", nativeQuery = true)
    Long findMaxPlanId();

    /**
     * Find medication plans whose remindTime falls within [from, to] for today,
     * are still in the initial state (remindStatus=0), and are active (isValid=1).
     */
    @Query("SELECT m FROM MedicationPlan m WHERE m.remindStatus = 0 AND m.isValid = 1 " +
           "AND m.remindTime >= :from AND m.remindTime <= :to " +
           "AND m.startDate <= :today AND (m.endDate IS NULL OR m.endDate >= :today)")
    List<MedicationPlan> findDueReminders(
            @Param("from") LocalTime from,
            @Param("to") LocalTime to,
            @Param("today") LocalDate today);

    /**
     * Find snoozed medication plans whose snooze period has elapsed.
     */
    @Query("SELECT m FROM MedicationPlan m WHERE m.remindStatus = 3 AND m.isValid = 1 " +
           "AND m.snoozeTime <= :now")
    List<MedicationPlan> findSnoozedRemindersReady(@Param("now") LocalDateTime now);
}