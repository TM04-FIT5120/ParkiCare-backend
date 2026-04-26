package com.caregiver.dto;

import lombok.Data;

@Data
public class AutoMedicationPlanRequest {

    private Long patientId;

    private Long drugId;

    // Example: "2026-04-26 08:00:00"
    private String startDateTime;

    // How many times per day
    private Integer timesPerDay;

    private String dosage;

    private String frequency;

    private String planNote;

    private String mealTiming;

    private Integer quantity;

    private String intakeMethod;

    private String endDate;

    private String recurrence;
}