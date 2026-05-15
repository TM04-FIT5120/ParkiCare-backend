package com.caregiver.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EventRecommendationRequest {

    @NotNull(message = "Caregiver ID cannot be null")
    private Long caregiverId;

    @NotNull(message = "Latitude cannot be null")
    private Double lat;

    @NotNull(message = "Longitude cannot be null")
    private Double lon;
}
