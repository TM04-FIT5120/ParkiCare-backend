package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaregiverEventOccurrenceResponse {

    private Long id;
    private Long caregiverId;
    private String sourceType;
    private Long sourceId;
    private String occurrenceStart;
    private Integer completed;
}
