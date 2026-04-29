package com.caregiver.controller;

import com.caregiver.dto.RecipeGenerateRequest;
import com.caregiver.service.RecipeGenerateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/recipe/generate")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RecipeGenerateController {

    private final RecipeGenerateService recipeGenerateService;

    @PostMapping(consumes = "application/json")
    public String generateRecipe(@RequestBody RecipeGenerateRequest request) {
        return recipeGenerateService.generateRecipe(request);
    }
}






