package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskResponse {

    private Long id;
    private Long patientId;
    @Translatable
    private String title;
    private String type;
    private String date;
    private String time;
    private String status;
    private String priority;
    @Translatable
    private String note;
}