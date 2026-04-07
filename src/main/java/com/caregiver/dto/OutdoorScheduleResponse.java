package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OutdoorScheduleResponse {

    private Long id;
    private Long patientId;
    private String outdoorTitle;
    private String startDatetime;
    private String endDatetime;
    private String prepareNote;
    private Integer isCompleted;
}