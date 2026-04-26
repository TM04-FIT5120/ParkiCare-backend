package com.caregiver.repository;

import com.caregiver.entity.DrugBase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugBaseRepository extends JpaRepository<DrugBase, Long> {
    List<DrugBase> findByDrugIdIn(List<Long> drugIds);

}
