package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MedicationSummaryDTO {

    private Long drugId;
    private String drugName;

    private Integer targetCount;
    private Integer actualCount;
    private Double completionRate;

    private Integer successDays;
    private Integer incompleteDays;
}