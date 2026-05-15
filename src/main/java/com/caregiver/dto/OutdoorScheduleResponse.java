package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OutdoorScheduleResponse {

    private Long id;
    private Long patientId;
    @Translatable
    private String outdoorTitle;
    private String startDatetime;
    private String endDatetime;
    @Translatable
    private String prepareNote;
    private Integer isCompleted;
    private String recurrence;
    private Integer isPinned;
}