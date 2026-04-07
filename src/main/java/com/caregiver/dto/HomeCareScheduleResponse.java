package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HomeCareScheduleResponse {

    private Long id;
    private Long patientId;
    private String homeCareTitle;
    private String startDatetime;
    private String endDatetime;
    private String careNote;
    private Integer isCompleted;
    private Integer isUrgent;
}