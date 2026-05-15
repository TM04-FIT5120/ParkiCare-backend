package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RecommendationFeedbackRequest {

    @NotBlank(message = "Feedback cannot be empty")
    private String userFeedback;
}
