package com.caregiver.entity;

import com.caregiver.model.EventOccurrenceSourceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "caregiver_event_occurrence",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ceo",
                columnNames = {"caregiver_id", "source_type", "source_id", "occurrence_start"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverEventOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private EventOccurrenceSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "occurrence_start", nullable = false)
    private LocalDateTime occurrenceStart;

    @Column(name = "completed", nullable = false)
    private Integer completed = 1;
}
