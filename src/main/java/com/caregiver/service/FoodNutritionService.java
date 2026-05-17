package com.caregiver.service;

import com.caregiver.dto.FoodNutritionResponse;
import com.caregiver.entity.FoodNutrition;
import com.caregiver.repository.FoodNutritionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodNutritionService {

    private final FoodNutritionRepository foodNutritionRepository;
    private final ContentTranslationService contentTranslationService;

    public List<FoodNutritionResponse> getAllFoodsLocalized(String locale) {
        return localize(foodNutritionRepository.findAll(), locale);
    }

    public FoodNutritionResponse getFoodByIdLocalized(Long id, String locale) {
        FoodNutrition food = getFoodById(id);
        return localize(List.of(food), locale).get(0);
    }

    public List<FoodNutritionResponse> getFoodsByCategoryLocalized(String category, String locale) {
        return localize(foodNutritionRepository.findByCategory(category), locale);
    }

    public List<FoodNutritionResponse> getFoodsBySafetyStatusLocalized(String safetyStatus, String locale) {
        return localize(foodNutritionRepository.findBySafetyStatus(safetyStatus), locale);
    }

    public List<FoodNutritionResponse> searchFoodsByNameLocalized(String keyword, String locale) {
        return localize(foodNutritionRepository.findByFoodNameContainingIgnoreCase(keyword), locale);
    }

    public List<FoodNutritionResponse> filterFoodsLocalized(
            String category, String safetyStatus, String keyword, String locale
    ) {
        return localize(filterFoods(category, safetyStatus, keyword), locale);
    }

    private List<FoodNutritionResponse> localize(List<FoodNutrition> entities, String locale) {
        return contentTranslationService.getLocalizedFoods(entities, locale);
    }

    public List<FoodNutrition> getAllFoods() {
        return foodNutritionRepository.findAll();
    }

    public FoodNutrition getFoodById(Long id) {
        return foodNutritionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Food not found"));
    }

    public List<FoodNutrition> getFoodsByCategory(String category) {
        return foodNutritionRepository.findByCategory(category);
    }

    public List<FoodNutrition> getFoodsBySafetyStatus(String safetyStatus) {
        return foodNutritionRepository.findBySafetyStatus(safetyStatus);
    }

    public List<FoodNutrition> searchFoodsByName(String keyword) {
        return foodNutritionRepository.findByFoodNameContainingIgnoreCase(keyword);
    }

    public List<FoodNutrition> filterFoods(String category, String safetyStatus, String keyword) {

        boolean hasCategory = category != null && !category.trim().isEmpty();
        boolean hasSafetyStatus = safetyStatus != null && !safetyStatus.trim().isEmpty();
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();

        if (hasCategory && hasSafetyStatus && hasKeyword) {
            return foodNutritionRepository
                    .findByCategoryAndSafetyStatusAndFoodNameContainingIgnoreCase(
                            category, safetyStatus, keyword
                    );
        }

        if (hasCategory && hasSafetyStatus) {
            return foodNutritionRepository.findByCategoryAndSafetyStatus(category, safetyStatus);
        }

        if (hasCategory && hasKeyword) {
            return foodNutritionRepository
                    .findByCategoryAndFoodNameContainingIgnoreCase(category, keyword);
        }

        if (hasSafetyStatus && hasKeyword) {
            return foodNutritionRepository
                    .findBySafetyStatusAndFoodNameContainingIgnoreCase(safetyStatus, keyword);
        }

        if (hasKeyword) {
            return searchFoodsByName(keyword);
        }

        if (hasCategory) {
            return getFoodsByCategory(category);
        }

        if (hasSafetyStatus) {
            return getFoodsBySafetyStatus(safetyStatus);
        }

        return getAllFoods();
    }
}