package com.caregiver.controller;

import com.caregiver.dto.RecipeGenerateRequest;
import com.caregiver.entity.GeneratedRecipe;
import com.caregiver.repository.GeneratedRecipeRepository;
import com.caregiver.service.RecipeGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recipe")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecipeGenerateController {

    private final RecipeGenerateService recipeGenerateService;
    private final GeneratedRecipeRepository generatedRecipeRepository;

    @PostMapping(value = "/generate", consumes = "application/json")
    public Map<String, Object> generateRecipe(@RequestBody RecipeGenerateRequest request) {
        return recipeGenerateService.generateRecipe(request);
    }

    @GetMapping("/history")
    public List<GeneratedRecipe> getHistory(@RequestParam Long caregiverId) {
        return generatedRecipeRepository.findByCaregiverIdOrderByCreatedAtDesc(caregiverId);
    }
}
