package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PushSubscriptionRequest {

    @NotNull(message = "caregiverId is required")
    private Long caregiverId;

    @NotBlank(message = "fcmToken is required")
    private String fcmToken;

    private String deviceType;
}
