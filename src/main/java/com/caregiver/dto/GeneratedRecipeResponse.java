package com.caregiver.dto;

import com.caregiver.entity.GeneratedRecipe;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbound DTO for generated recipe history. Localized text fields are merged
 * from {@code content_translation} by {@code ContentTranslationService}.
 */
@Data
@NoArgsConstructor
public class GeneratedRecipeResponse {

    private Long id;
    private Long caregiverId;
    private String inputFoods;

    private String recipeTitle;

    private String ingredients;

    private String steps;

    private String suitableDesc;

    private String unsuitableDesc;

    private String healthTip;

    private String category;

    private String highProteinWarning;

    private String referenceSource;
    private LocalDateTime createdAt;

    public static GeneratedRecipeResponse from(GeneratedRecipe entity) {
        if (entity == null) return null;
        GeneratedRecipeResponse dto = new GeneratedRecipeResponse();
        dto.setId(entity.getId());
        dto.setCaregiverId(entity.getCaregiverId());
        dto.setInputFoods(entity.getInputFoods());
        dto.setRecipeTitle(entity.getRecipeTitle());
        dto.setIngredients(entity.getIngredients());
        dto.setSteps(entity.getSteps());
        dto.setSuitableDesc(entity.getSuitableDesc());
        dto.setUnsuitableDesc(entity.getUnsuitableDesc());
        dto.setHealthTip(entity.getHealthTip());
        dto.setCategory(entity.getCategory());
        dto.setHighProteinWarning(entity.getHighProteinWarning());
        dto.setReferenceSource(entity.getReferenceSource());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }
}
