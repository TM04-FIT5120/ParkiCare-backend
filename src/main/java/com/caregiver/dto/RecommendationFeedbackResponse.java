package com.caregiver.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code POST /api/eventRecommendation/feedback}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RecommendationFeedbackResponse {

    /** {@code true} only when the request was valid and processing completed (HTTP 200). */
    private boolean success;

    /** Short success message; {@code null} when {@code success} is {@code false}. */
    private String message;

    /** Human-readable failure reason; {@code null} when {@code success} is {@code true}. */
    private String error;

    public static RecommendationFeedbackResponse okay() {
        return new RecommendationFeedbackResponse(
                true,
                "Feedback saved. If accepted, associated schedules were updated.",
                null
        );
    }

    public static RecommendationFeedbackResponse fail(String errorMessage) {
        return new RecommendationFeedbackResponse(false, null, errorMessage);
    }
}
