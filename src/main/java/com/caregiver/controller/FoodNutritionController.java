package com.caregiver.controller;

import com.caregiver.entity.FoodNutrition;
import com.caregiver.service.FoodNutritionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FoodNutritionController {

    private final FoodNutritionService foodNutritionService;

    @GetMapping
    public List<FoodNutrition> getAllFoods() {
        return foodNutritionService.getAllFoods();
    }

    @GetMapping("/{id}")
    public FoodNutrition getFoodById(@PathVariable Long id) {
        return foodNutritionService.getFoodById(id);
    }

    @GetMapping("/category/{category}")
    public List<FoodNutrition> getFoodsByCategory(@PathVariable String category) {
        return foodNutritionService.getFoodsByCategory(category);
    }

    @GetMapping("/status/{safetyStatus}")
    public List<FoodNutrition> getFoodsBySafetyStatus(@PathVariable String safetyStatus) {
        return foodNutritionService.getFoodsBySafetyStatus(safetyStatus);
    }

    @GetMapping("/search")
    public List<FoodNutrition> searchFoods(@RequestParam String keyword) {
        return foodNutritionService.searchFoodsByName(keyword);
    }

    @GetMapping("/filter")
    public List<FoodNutrition> filterFoods(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String safetyStatus,
            @RequestParam(required = false) String keyword
    ) {
        return foodNutritionService.filterFoods(category, safetyStatus, keyword);
    }
}