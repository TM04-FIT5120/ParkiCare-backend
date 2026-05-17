package com.caregiver.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MedicationPlanNoteRequest {

    @Size(max = 600, message = "planNote length must be <= 600")
    private String planNote;
}
