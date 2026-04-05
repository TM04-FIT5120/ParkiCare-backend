package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Unique ID cannot be empty")
    private String uniqueId;

    @NotBlank(message = "Password cannot be empty")
    private String password;
}