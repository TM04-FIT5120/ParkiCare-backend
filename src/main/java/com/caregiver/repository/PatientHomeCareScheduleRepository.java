package com.caregiver.repository;

import com.caregiver.entity.PatientHomeCareSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PatientHomeCareScheduleRepository extends JpaRepository<PatientHomeCareSchedule, Long> {

    List<PatientHomeCareSchedule> findByPatientIdAndIsDeleted(Long patientId, Integer isDeleted);

    List<PatientHomeCareSchedule> findByPatientIdAndIsDeletedAndIsCompletedAndScheduleSourceAndStartDatetimeGreaterThanEqualAndStartDatetimeLessThan(
            Long patientId,
            Integer isDeleted,
            Integer isCompleted,
            String scheduleSource,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive);
}