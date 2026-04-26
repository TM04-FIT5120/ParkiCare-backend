package com.caregiver.dto;

import lombok.Data;

@Data
public class AutoMedicationPlanRequest {

    private Long patientId;
    private Long drugId;

    // 服药开始日期，例如 "2026-04-26"
    private String startDate;

    // 服药截止日期，例如 "2026-04-28"
    private String endDate;

    // 每天第一次服药时间，例如 "08:00"
    private String dailyStartTime;

    // 每天几次，例如 3
    private Integer timesPerDay;

    private String dosage;
    private String frequency;

    private String planNote;
    private String mealTiming;
    private Integer quantity;
    private String intakeMethod;
    private String recurrence;
}