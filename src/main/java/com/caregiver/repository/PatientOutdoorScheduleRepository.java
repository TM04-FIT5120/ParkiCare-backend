package com.caregiver.repository;

import com.caregiver.entity.PatientOutdoorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PatientOutdoorScheduleRepository extends JpaRepository<PatientOutdoorSchedule, Long> {

    List<PatientOutdoorSchedule> findByPatientIdAndIsDeleted(Long patientId, Integer isDeleted);

    List<PatientOutdoorSchedule> findByPatientIdAndIsDeletedAndIsCompletedAndScheduleSourceAndStartDatetimeGreaterThanEqualAndStartDatetimeLessThan(
            Long patientId,
            Integer isDeleted,
            Integer isCompleted,
            String scheduleSource,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive);
}