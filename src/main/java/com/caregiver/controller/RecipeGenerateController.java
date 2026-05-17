package com.caregiver.controller;

import com.caregiver.dto.GeneratedRecipeResponse;
import com.caregiver.dto.RecipeGenerateRequest;
import com.caregiver.repository.GeneratedRecipeRepository;
import com.caregiver.service.ContentTranslationService;
import com.caregiver.service.RecipeGenerateService;
import com.caregiver.util.LocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ContentTranslationService contentTranslationService;

    @PostMapping(value = "/generate", consumes = "application/json")
    public Map<String, Object> generateRecipe(@RequestBody RecipeGenerateRequest request) {
        return recipeGenerateService.generateRecipe(request);
    }

    @GetMapping("/history")
    public List<GeneratedRecipeResponse> getHistory(
            @RequestParam Long caregiverId,
            HttpServletRequest request
    ) {
        String locale = LocaleResolver.normalize(request.getHeader("Accept-Language"));
        var entities = generatedRecipeRepository.findByCaregiverIdOrderByCreatedAtDesc(caregiverId);
        return contentTranslationService.applyRecipeTranslationsWithLazyFill(entities, locale);
    }
}
