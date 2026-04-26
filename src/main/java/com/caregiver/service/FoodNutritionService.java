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

        if (keyword != null && !keyword.trim().isEmpty()) {
            return searchFoodsByName(keyword);
        }

        if (category != null && !category.trim().isEmpty()
                && safetyStatus != null && !safetyStatus.trim().isEmpty()) {
            return foodNutritionRepository.findByCategoryAndSafetyStatus(category, safetyStatus);
        }

        if (category != null && !category.trim().isEmpty()) {
            return getFoodsByCategory(category);
        }

        if (safetyStatus != null && !safetyStatus.trim().isEmpty()) {
            return getFoodsBySafetyStatus(safetyStatus);
        }

        return getAllFoods();
    }
}
