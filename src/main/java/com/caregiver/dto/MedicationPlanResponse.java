package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MedicationPlanResponse {

    private Long remindId;
    private Long patientId;
    private Long drugId;

    private String dosage;
    private String frequency;

    private String date;
    private String time;

    private String status;
    private String note;

    private String mealTiming;
    private Integer quantity;
    private String intakeMethod;
    private String endDate;
    private String recurrence;
}