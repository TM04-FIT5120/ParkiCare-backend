package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class HomeCareScheduleRequest {

    @NotNull(message = "Patient ID cannot be null")
    private Long patientId;

    @NotNull(message = "Caregiver ID cannot be null")
    private Long caregiverId;

    @NotBlank(message = "Home care title cannot be empty")
    private String homeCareTitle;

    @NotBlank(message = "Start datetime cannot be empty")
    private String startDatetime;

    @NotBlank(message = "End datetime cannot be empty")
    private String endDatetime;

    private String careNote;
    private Integer isCompleted;
    private Integer isUrgent;
    private String recurrence;
}