package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typed error envelope so the response translation advice picks up the
 * caregiver-facing message via {@link Translatable}. The {@code Map}-based
 * shape used previously was skipped by the advice and surfaced English errors
 * to non-English clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    private boolean success;

    @Translatable
    private String error;

    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse(false, message);
    }
}
