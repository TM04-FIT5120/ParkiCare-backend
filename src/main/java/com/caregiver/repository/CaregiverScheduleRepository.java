package com.caregiver.repository;

import com.caregiver.entity.CaregiverSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaregiverScheduleRepository extends JpaRepository<CaregiverSchedule, Long> {

    List<CaregiverSchedule> findByCaregiverIdAndIsDeleted(Long caregiverId, Integer isDeleted);
}