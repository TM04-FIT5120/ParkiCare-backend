package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MedicationPlanNoteResponse {
    private Long remindId;
    private String planNote;
}

