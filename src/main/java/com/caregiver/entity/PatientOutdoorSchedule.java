package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_outdoor_schedule")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientOutdoorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "outdoor_title", nullable = false, length = 100)
    private String outdoorTitle;

    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDatetime;

    @Column(name = "prepare_note", length = 200)
    private String prepareNote;

    @Column(name = "is_completed", nullable = false)
    private Integer isCompleted = 0;

    @Column(name = "recurrence", length = 20)
    private String recurrence;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;

    @Column(name = "is_pinned", nullable = false)
    private Integer isPinned = 0;
}