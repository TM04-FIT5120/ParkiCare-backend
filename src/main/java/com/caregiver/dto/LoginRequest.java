package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Password cannot be empty")
    private String password;

    @NotBlank(message = "nickname cannot be empty")
    private String nickname;
}