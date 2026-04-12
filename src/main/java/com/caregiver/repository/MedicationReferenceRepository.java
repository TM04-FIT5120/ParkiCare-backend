package com.caregiver.repository;

import com.caregiver.entity.DrugBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MedicationReferenceRepository extends JpaRepository<DrugBase, Long> {

    List<DrugBase> findByDrugNameContainingIgnoreCaseAndIsValid(String keyword, Integer isValid);

    Optional<DrugBase> findByDrugId(Long drugId);

    @Query("SELECT DISTINCT d.manufacturerName FROM DrugBase d WHERE d.manufacturerName IS NOT NULL AND LOWER(d.manufacturerName) LIKE LOWER(CONCAT('%', :keyword, '%')) AND d.isValid = 1")
    List<String> findDistinctManufacturerNamesByKeyword(@Param("keyword") String keyword);
}