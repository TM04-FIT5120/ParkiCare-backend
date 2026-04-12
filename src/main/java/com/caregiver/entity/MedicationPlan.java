package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "medication_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicationPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "remind_id")
    private Long remindId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "drug_id", nullable = false)
    private Long drugId;

    @Column(name = "dosage", nullable = false, length = 30)
    private String dosage;

    @Column(name = "frequency", nullable = false, length = 20)
    private String frequency;

    @Column(name = "admin_time", nullable = false)
    private LocalTime adminTime;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "is_overdue", nullable = false)
    private Integer isOverdue = 0;

    @Column(name = "snooze_time")
    private LocalDateTime snoozeTime;

    @Column(name = "remind_time", nullable = false)
    private LocalTime remindTime;

    @Column(name = "remind_status", nullable = false)
    private Integer remindStatus = 0;

    @Column(name = "is_valid", nullable = false)
    private Integer isValid = 1;

    @Column(name = "plan_note", length = 200)
    private String planNote;

    @Column(name = "meal_timing", length = 20)
    private String mealTiming;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "intake_method", length = 100)
    private String intakeMethod;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "recurrence", length = 20)
    private String recurrence;
}
