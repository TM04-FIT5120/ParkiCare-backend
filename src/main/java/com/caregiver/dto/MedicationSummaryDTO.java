package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MedicationSummaryDTO {

    private Long drugId;

    @Translatable
    private String drugName;

    private Integer targetCount;
    private Integer actualCount;
    private Double completionRate;

    private Integer successDays;
    private Integer incompleteDays;
}