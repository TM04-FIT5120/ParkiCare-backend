package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_home_care_schedule")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientHomeCareSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "home_care_title", nullable = false, length = 100)
    private String homeCareTitle;

    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDatetime;

    @Column(name = "care_note", length = 200)
    private String careNote;

    @Column(name = "is_completed", nullable = false)
    private Integer isCompleted = 0;

    @Column(name = "is_urgent", nullable = false)
    private Integer isUrgent = 0;

    @Column(name = "recurrence", length = 20)
    private String recurrence;
}
