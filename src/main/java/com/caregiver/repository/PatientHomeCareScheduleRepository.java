package com.caregiver.repository;

import com.caregiver.entity.PatientHomeCareSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PatientHomeCareScheduleRepository extends JpaRepository<PatientHomeCareSchedule, Long> {

    List<PatientHomeCareSchedule> findByPatientId(Long patientId);
}