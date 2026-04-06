package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatientRequest {

    @NotNull(message = "Caregiver ID cannot be null")
    private Long caregiverId;

    @NotBlank(message = "Patient nickname cannot be empty")
    @Size(max = 100, message = "Patient nickname cannot exceed 100 characters")
    private String patientNickname;

    @Size(max = 50, message = "Age range cannot exceed 50 characters")
    private String ageRange;

    @Size(max = 500, message = "Remark cannot exceed 500 characters")
    private String remark;
}