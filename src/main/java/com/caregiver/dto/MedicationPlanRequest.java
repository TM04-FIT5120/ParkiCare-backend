package com.caregiver.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MedicationPlanRequest {

    @NotNull(message = "Patient ID cannot be null")
    private Long patientId;

    @NotNull(message = "Drug ID cannot be null")
    private Long drugId;

    @NotNull(message = "Dosage cannot be null")
    private String dosage;

    @NotNull(message = "Frequency cannot be null")
    private String frequency;

    // 例如 ["08:00", "12:00", "20:00"]
    @NotEmpty(message = "At least one administration time is required")
    private String adminTimes;

    @NotNull(message = "remindTime date cannot be null")
    private String remindTime;

    // 格式：2026-04-06
    @NotNull(message = "Start date cannot be null")
    private String startDate;

    private String planNote;
}