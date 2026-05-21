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


    @Query("SELECT m FROM MedicationPlan m WHERE m.isValid = 1 " +
            "AND m.remindTime = :targetTime " +
            "AND m.startDate <= :targetDate AND (m.endDate IS NULL OR m.endDate >= :targetDate)")
    List<MedicationPlan> findActiveByRemindTimeOnDate(
            @Param("targetTime") LocalTime targetTime,
            @Param("targetDate") LocalDate targetDate);

    /**
     * Return all pending/snoozed plans for a caregiver across all their patients.
     * Used by the frontend when an FCM notification arrives without data fields
     * (FCM Web Push strips data - we fall back to polling the API).
     */
    @Query("SELECT m FROM MedicationPlan m " +
           "JOIN Patient p ON m.patientId = p.id " +
           "WHERE p.caregiverId = :caregiverId " +
           "AND m.remindStatus IN (1, 3) AND m.isValid = 1 " +
           "ORDER BY m.remindTime ASC")
    List<MedicationPlan> findPendingByCaregiver(@Param("caregiverId") Long caregiverId);


    @Query("""
    SELECT m FROM MedicationPlan m
    WHERE m.planId = :planId
      AND m.startDate = :date
      AND m.remindTime > :remindTime
      AND m.isValid = 1
      AND m.remindStatus IN (0, 1, 3)
    ORDER BY m.remindTime ASC
""")
    List<MedicationPlan> findFollowingRemindersByPlanId(
            @Param("planId") Long planId,
            @Param("date") LocalDate date,
            @Param("remindTime") LocalTime remindTime
    );

    /**
     * Find confirmed medication plans whose 20-minute observation window has elapsed
     * and have not yet had an observation notification sent.
     */
    @Query("SELECT m FROM MedicationPlan m WHERE m.observationNotified = 0 " +
           "AND m.observationDueTime IS NOT NULL AND m.observationDueTime <= :now " +
           "AND m.remindStatus = 2 AND m.isValid = 1")
    List<MedicationPlan> findDueObservations(@Param("now") LocalDateTime now);

    /**
     * 当日「全天」用药计划行（不区分 is_valid 0/1）：用于 on/off、事件推荐 medicationTimeList。
     * 含已完成与未完成排程；条件：startDate≤today，endDate 为空或≥today。
     */
    @Query("SELECT m FROM MedicationPlan m WHERE m.patientId = :patientId "
            + "AND m.startDate <= :today AND (m.endDate IS NULL OR m.endDate >= :today)")
    List<MedicationPlan> findAllPlansForPatientOnCalendarDay(
            @Param("patientId") Long patientId,
            @Param("today") LocalDate today);

    @Query("""
            SELECT m FROM MedicationPlan m
            JOIN Patient p ON m.patientId = p.id
            WHERE p.caregiverId = :caregiverId
              AND m.anchoredMeals IS NOT NULL AND TRIM(m.anchoredMeals) <> ''
              AND m.mealTiming IS NOT NULL AND TRIM(m.mealTiming) <> ''
              AND m.isValid = 1
              AND m.startDate <= :today
              AND (m.endDate IS NULL OR m.endDate >= :today)
            """)
    List<MedicationPlan> findMealAnchoredActiveByCaregiver(
            @Param("caregiverId") Long caregiverId,
            @Param("today") LocalDate today);

    /**
     * Find active, unconfirmed plans whose adminTime crossed the overdue threshold
     * (cutoffDate/cutoffTime = now minus grace period) and are not yet flagged overdue.
     * Covers remindStatus 0 (never fired), 1 (pending), and 3 (snoozed).
     */
    @Query("SELECT m FROM MedicationPlan m WHERE m.isValid = 1 " +
           "AND m.remindStatus IN (0, 1, 3) " +
           "AND m.isOverdue = 0 " +
           "AND (m.startDate < :cutoffDate " +
           "     OR (m.startDate = :cutoffDate AND m.adminTime <= :cutoffTime))")
    List<MedicationPlan> findOverdueEligible(
            @Param("cutoffDate") LocalDate cutoffDate,
            @Param("cutoffTime") LocalTime cutoffTime);

}