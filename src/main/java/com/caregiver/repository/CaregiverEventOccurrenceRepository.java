package com.caregiver.repository;

import com.caregiver.entity.CaregiverEventOccurrence;
import com.caregiver.model.EventOccurrenceSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CaregiverEventOccurrenceRepository extends JpaRepository<CaregiverEventOccurrence, Long> {

    List<CaregiverEventOccurrence> findByCaregiverIdAndOccurrenceStartGreaterThanEqualAndOccurrenceStartBefore(
            Long caregiverId,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    );

    Optional<CaregiverEventOccurrence> findByCaregiverIdAndSourceTypeAndSourceIdAndOccurrenceStart(
            Long caregiverId,
            EventOccurrenceSourceType sourceType,
            Long sourceId,
            LocalDateTime occurrenceStart
    );

    void deleteByCaregiverIdAndSourceTypeAndSourceIdAndOccurrenceStart(
            Long caregiverId,
            EventOccurrenceSourceType sourceType,
            Long sourceId,
            LocalDateTime occurrenceStart
    );
}
