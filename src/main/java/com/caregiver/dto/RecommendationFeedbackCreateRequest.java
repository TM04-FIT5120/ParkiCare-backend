package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class RecommendationFeedbackCreateRequest {

    @NotNull(message = "Caregiver ID cannot be null")
    private Long caregiverId;

    @NotNull(message = "Latitude cannot be null")
    private Double lat;

    @NotNull(message = "Longitude cannot be null")
    private Double lon;

    @NotBlank(message = "Event name cannot be empty")
    @Translatable
    private String eventName;

    @NotBlank(message = "Period cannot be empty")
    private String period;

    @NotBlank(message = "Type cannot be empty")
    private String type;

    @NotBlank(message = "Start time cannot be empty")
    private String startTime;

    @NotNull(message = "Duration cannot be null")
    @Min(value = 15, message = "Duration must be at least 15 minutes")
    @Max(value = 60, message = "Duration must be at most 60 minutes")
    private Integer durationMinutes;

    @NotBlank(message = "Remark cannot be empty")
    @Translatable
    private String remark;

    @NotBlank(message = "Feedback cannot be empty")
    private String userFeedback;
}
