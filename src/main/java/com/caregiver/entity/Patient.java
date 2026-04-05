package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "patient")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "patient_nickname", nullable = false, length = 100)
    private String patientNickname;

    @Column(name = "age_range", length = 50)
    private String ageRange;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;
}