package com.caregiver.repository;

import com.caregiver.entity.DrugBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationReferenceRepository extends JpaRepository<DrugBase, Long> {

    List<DrugBase> findByDrugNameContainingIgnoreCaseAndIsValid(String keyword, Integer isValid);

    Optional<DrugBase> findByDrugId(Long drugId);
}