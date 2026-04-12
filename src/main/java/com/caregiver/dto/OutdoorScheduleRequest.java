package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OutdoorScheduleRequest {

    @NotNull(message = "Patient ID cannot be null")
    private Long patientId;

    @NotNull(message = "Caregiver ID cannot be null")
    private Long caregiverId;

    @NotBlank(message = "Outdoor title cannot be empty")
    private String outdoorTitle;

    @NotBlank(message = "Start datetime cannot be empty")
    private String startDatetime;

    @NotBlank(message = "End datetime cannot be empty")
    private String endDatetime;

    private String prepareNote;
    private Integer isCompleted;
    private String recurrence;
}