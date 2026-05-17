package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaregiverAlertResponse {
    private Long id;
    private String alertType;
    private String payloadJson;
    private String createdAt;
}
