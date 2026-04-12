package com.caregiver.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpsertCaregiverEventOccurrenceRequest {

    @NotNull
    private Long caregiverId;

    @NotNull
    private String sourceType;

    @NotNull
    private Long sourceId;

    @NotNull
    private String occurrenceStart;

    @NotNull
    private Boolean completed;
}
