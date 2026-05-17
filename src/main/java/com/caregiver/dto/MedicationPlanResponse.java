package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
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

    @Translatable
    private String frequency;

    private String date;
    private String time;

    private String status;

    @Translatable
    private String note;

    @Translatable
    private String mealTiming;

    private Integer quantity;

    @Translatable
    private String intakeMethod;

    private String endDate;
    private String recurrence;
}