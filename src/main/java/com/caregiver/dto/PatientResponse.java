package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PatientResponse {

    private Long id;
    private Long caregiverId;
    private String patientNickname;
    private String ageRange;
    private String remark;
}