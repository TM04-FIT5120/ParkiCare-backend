package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Nickname cannot be empty")
    @Size(min = 6, max = 20, message = "Nickname must be between 6 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Nickname must contain only letters and numbers")
    private String nickname;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, max = 20, message = "Password must be between 6 and 20 characters")
    private String password;
}