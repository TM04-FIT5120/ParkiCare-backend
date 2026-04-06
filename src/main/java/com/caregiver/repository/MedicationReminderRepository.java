package com.caregiver.repository;

import com.caregiver.entity.MedicationPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MedicationReminderRepository extends JpaRepository<MedicationPlan, Long> {

    Optional<MedicationPlan> findByRemindId(Long remindId);

    List<MedicationPlan> findByPatientId(Long patientId);

    List<MedicationPlan> findByPatientIdAndStartDate(Long patientId, LocalDate startDate);

    List<MedicationPlan> findByPatientIdAndRemindStatusIn(Long patientId, List<Integer> statuses);

    List<MedicationPlan> findByPatientIdIn(List<Long> patientIds);

    @Query(value = "SELECT MAX(plan_id) FROM medication_plan", nativeQuery = true)
    Long findMaxPlanId();
}