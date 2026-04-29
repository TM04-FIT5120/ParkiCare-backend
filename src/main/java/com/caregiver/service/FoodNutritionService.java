package com.caregiver.service;

import com.caregiver.entity.FoodNutrition;
import com.caregiver.repository.FoodNutritionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FoodNutritionService {

    private final FoodNutritionRepository foodNutritionRepository;

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