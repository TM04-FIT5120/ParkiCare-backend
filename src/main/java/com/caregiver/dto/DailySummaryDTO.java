package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailySummaryDTO {

    private String date;

    private Integer targetCount;
    private Integer actualCount;
    private Double completionRate;

    private String status;
}