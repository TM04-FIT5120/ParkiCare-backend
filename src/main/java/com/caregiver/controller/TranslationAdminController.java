package com.caregiver.controller;

import com.caregiver.repository.FoodNutritionRepository;
import com.caregiver.service.ContentTranslationService;
import com.caregiver.util.LocaleResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/translations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TranslationAdminController {

    private final FoodNutritionRepository foodNutritionRepository;
    private final ContentTranslationService contentTranslationService;

    @Value("${translation.admin.key:}")
    private String adminKey;

    @PostMapping("/backfill/foods")
    public Map<String, Object> backfillFoods(
            @RequestParam(defaultValue = "zh-CN,ms-MY") String locales,
            @RequestHeader(value = "X-Admin-Key", required = false) String requestKey
    ) {
        if (adminKey != null && !adminKey.isBlank()) {
            if (requestKey == null || !adminKey.equals(requestKey)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin key");
            }
        }

        List<String> localeList = Arrays.stream(locales.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank() && !LocaleResolver.isEnglish(s))
                .collect(Collectors.toList());

        if (localeList.isEmpty()) {
            localeList = List.copyOf(LocaleResolver.TRANSLATABLE_LOCALES);
        }

        var foods = foodNutritionRepository.findAll();
        ContentTranslationService.BackfillResult result =
                contentTranslationService.backfillFoods(foods, localeList);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entitiesProcessed", result.entitiesProcessed());
        body.put("rowsUpserted", result.rowsUpserted());
        body.put("failures", result.failures());
        body.put("locales", localeList);
        return body;
    }
}
