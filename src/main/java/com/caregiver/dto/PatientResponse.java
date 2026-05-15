package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
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
    @Translatable
    private String remark;
}