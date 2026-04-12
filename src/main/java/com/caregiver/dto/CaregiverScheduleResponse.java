package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaregiverScheduleResponse {

    private Long id;
    private Long caregiverId;
    private String scheduleTitle;
    private String startDatetime;
    private String endDatetime;
    private String scheduleNote;
    private String recurrence;
    private Integer isCompleted;
    private Integer isConflict;
}