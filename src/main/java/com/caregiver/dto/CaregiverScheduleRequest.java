package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CaregiverScheduleRequest {

    @NotNull(message = "Caregiver ID cannot be null")
    private Long caregiverId;

    @NotBlank(message = "Schedule title cannot be empty")
    private String scheduleTitle;

    @NotBlank(message = "Start datetime cannot be empty")
    private String startDatetime;

    @NotBlank(message = "End datetime cannot be empty")
    private String endDatetime;

    private String scheduleNote;

    private String recurrence;

    private Integer isCompleted;

    private Integer isConflict;
}