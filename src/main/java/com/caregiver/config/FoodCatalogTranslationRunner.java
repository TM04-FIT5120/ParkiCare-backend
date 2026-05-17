package com.caregiver.config;

import com.caregiver.entity.FoodNutrition;
import com.caregiver.repository.FoodNutritionRepository;
import com.caregiver.service.ContentTranslationService;
import com.caregiver.service.TranslationService;
import com.caregiver.util.LocaleResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup, batch-translates the static food catalog into content_translation
 * when rows are missing for zh-CN / ms-MY.
 */
@Component
@ConditionalOnProperty(name = "translation.food.auto-backfill-on-startup", havingValue = "true")
public class FoodCatalogTranslationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FoodCatalogTranslationRunner.class);

    private final FoodNutritionRepository foodNutritionRepository;
    private final ContentTranslationService contentTranslationService;
    private final TranslationService translationService;

    @Value("${translation.enabled:true}")
    private boolean translationEnabled;

    public FoodCatalogTranslationRunner(
            FoodNutritionRepository foodNutritionRepository,
            ContentTranslationService contentTranslationService,
            TranslationService translationService
    ) {
        this.foodNutritionRepository = foodNutritionRepository;
        this.contentTranslationService = contentTranslationService;
        this.translationService = translationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!translationEnabled || !translationService.isTranslationAvailable()) {
            log.info("Food catalog startup translation skipped (translation disabled or no API key).");
            return;
        }

        List<FoodNutrition> foods = foodNutritionRepository.findAll();
        if (foods.isEmpty()) {
            return;
        }

        for (String locale : LocaleResolver.TRANSLATABLE_LOCALES) {
            if (contentTranslationService.isFoodCatalogComplete(foods, locale)) {
                log.info("Food catalog already translated for {}", locale);
                continue;
            }
            log.info("Scheduling startup food catalog translation for {} ({} items)", locale, foods.size());
            contentTranslationService.ensureFoodCatalogBatchAsync(foods, locale);
        }
    }
}
