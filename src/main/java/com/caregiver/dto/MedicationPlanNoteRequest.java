package com.caregiver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MedicationPlanNoteRequest {

    @NotBlank(message = "planNote cannot be blank")
    @Size(max = 200, message = "planNote length must be <= 200")
    private String planNote;
}

