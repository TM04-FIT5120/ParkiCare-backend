package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
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
    @Translatable
    private String careNote;
    private Integer isCompleted;
    private Integer isUrgent;
    private String recurrence;
    private Integer isPinned;
}