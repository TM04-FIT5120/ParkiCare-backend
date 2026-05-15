package com.caregiver.dto;

import lombok.Data;

import java.util.List;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RecipeGenerateRequest {
    private List<String> foods;
    private Long caregiverId;
}
