package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailyMedicationDTO {

    private String date;

    private Long drugId;
    private String drugName;

    private Integer targetFrequency;
    private Integer actualCount;

    private Double completionRate;

    private String status;
}