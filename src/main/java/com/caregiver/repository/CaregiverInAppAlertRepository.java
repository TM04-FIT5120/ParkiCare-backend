package com.caregiver.repository;

import com.caregiver.entity.CaregiverInAppAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaregiverInAppAlertRepository extends JpaRepository<CaregiverInAppAlert, Long> {

    List<CaregiverInAppAlert> findByCaregiverIdAndReadAtIsNullOrderByCreatedAtDesc(Long caregiverId);
}
