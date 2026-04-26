package com.caregiver.repository;

import com.caregiver.entity.MedicationPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MedicationPlanRepository extends JpaRepository<MedicationPlan, Long> {

    List<MedicationPlan> findByPatientIdAndStartDateBetweenAndIsValid(
            Long patientId,
            LocalDate startDate,
            LocalDate endDate,
            Integer isValid
    );
}