package com.caregiver.controller;

import com.caregiver.dto.FoodNutritionResponse;
import com.caregiver.service.FoodNutritionService;
import com.caregiver.util.LocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
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
    public List<FoodNutritionResponse> getAllFoods(HttpServletRequest request) {
        return foodNutritionService.getAllFoodsLocalized(resolveLocale(request));
    }

    @GetMapping("/{id}")
    public FoodNutritionResponse getFoodById(@PathVariable Long id, HttpServletRequest request) {
        return foodNutritionService.getFoodByIdLocalized(id, resolveLocale(request));
    }

    @GetMapping("/category")
    public List<FoodNutritionResponse> getFoodsByCategory(
            @RequestParam String category,
            HttpServletRequest request
    ) {
        return foodNutritionService.getFoodsByCategoryLocalized(category, resolveLocale(request));
    }

    @GetMapping("/status")
    public List<FoodNutritionResponse> getFoodsBySafetyStatus(
            @RequestParam String safetyStatus,
            HttpServletRequest request
    ) {
        return foodNutritionService.getFoodsBySafetyStatusLocalized(safetyStatus, resolveLocale(request));
    }

    @GetMapping("/search")
    public List<FoodNutritionResponse> searchFoods(
            @RequestParam String keyword,
            HttpServletRequest request
    ) {
        return foodNutritionService.searchFoodsByNameLocalized(keyword, resolveLocale(request));
    }

    @GetMapping("/filter")
    public List<FoodNutritionResponse> filterFoods(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String safetyStatus,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        return foodNutritionService.filterFoodsLocalized(
                category, safetyStatus, keyword, resolveLocale(request)
        );
    }

    private static String resolveLocale(HttpServletRequest request) {
        return LocaleResolver.normalize(request.getHeader("Accept-Language"));
    }
}
