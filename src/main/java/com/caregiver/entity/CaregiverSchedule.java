package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "caregiver_schedule")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "schedule_title", nullable = false, length = 100)
    private String scheduleTitle;

    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDatetime;

    @Column(name = "schedule_note", length = 200)
    private String scheduleNote;

    @Column(name = "recurrence", length = 20)
    private String recurrence;

    @Column(name = "is_completed", nullable = false)
    private Integer isCompleted = 0;

    @Column(name = "is_conflict", nullable = false)
    private Integer isConflict = 0;
}