package com.caregiver.repository;

import com.caregiver.entity.FoodNutrition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodNutritionRepository extends JpaRepository<FoodNutrition, Long> {

    List<FoodNutrition> findByCategory(String category);

    List<FoodNutrition> findBySafetyStatus(String safetyStatus);

    List<FoodNutrition> findByFoodNameContainingIgnoreCase(String foodName);

    List<FoodNutrition> findByCategoryAndSafetyStatus(String category, String safetyStatus);

    List<FoodNutrition> findByCategoryAndSafetyStatusAndFoodNameContainingIgnoreCase(
            String category,
            String safetyStatus,
            String foodName
    );

    List<FoodNutrition> findByCategoryAndFoodNameContainingIgnoreCase(
            String category,
            String foodName
    );

    List<FoodNutrition> findBySafetyStatusAndFoodNameContainingIgnoreCase(
            String safetyStatus,
            String foodName
    );

    List<FoodNutrition> findByFoodNameIn(List<String> foodNames);

}