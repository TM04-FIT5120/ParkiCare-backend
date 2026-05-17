package com.caregiver.dto;

import com.caregiver.entity.FoodNutrition;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Outbound DTO for food catalog responses. Localized fields (foodName, remark, measure)
 * are merged from {@code content_translation} by {@code ContentTranslationService}.
 * category and safetyStatus stay canonical English for client-side filtering.
 */
@Data
@NoArgsConstructor
public class FoodNutritionResponse {

    private Long id;

    /** Localized display name (may be overwritten from content_translation). */
    private String foodName;

    /** Canonical English name from food_nutrition — use for recipe generation / DB lookups. */
    private String canonicalFoodName;

    private String normalizedFoodname;

    private String category;

    private String safetyStatus;

    private String measure;
    private BigDecimal grams;
    private BigDecimal calories;
    private BigDecimal protein100g;
    private BigDecimal saturatedFats100g;
    private BigDecimal fat100g;
    private BigDecimal fiber100g;
    private BigDecimal carbs100g;

    private String remark;

    private String source;
    private String imageUrl;

    public static FoodNutritionResponse from(FoodNutrition entity) {
        if (entity == null) return null;
        FoodNutritionResponse dto = new FoodNutritionResponse();
        dto.setId(entity.getId());
        dto.setFoodName(entity.getFoodName());
        dto.setCanonicalFoodName(entity.getFoodName());
        dto.setNormalizedFoodname(entity.getNormalizedFoodname());
        dto.setCategory(entity.getCategory());
        dto.setSafetyStatus(entity.getSafetyStatus());
        dto.setMeasure(entity.getMeasure());
        dto.setGrams(entity.getGrams());
        dto.setCalories(entity.getCalories());
        dto.setProtein100g(entity.getProtein100g());
        dto.setSaturatedFats100g(entity.getSaturatedFats100g());
        dto.setFat100g(entity.getFat100g());
        dto.setFiber100g(entity.getFiber100g());
        dto.setCarbs100g(entity.getCarbs100g());
        dto.setRemark(entity.getRemark());
        dto.setSource(entity.getSource());
        dto.setImageUrl(entity.getImageUrl());
        return dto;
    }
}
