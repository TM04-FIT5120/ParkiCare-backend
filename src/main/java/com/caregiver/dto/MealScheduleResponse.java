package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealScheduleResponse {
    private Long caregiverId;
    private String mealType;
    private String mealTime; // "HH:mm"
}
