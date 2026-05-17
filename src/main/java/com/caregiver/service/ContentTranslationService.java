package com.caregiver.service;

import com.caregiver.dto.FoodNutritionResponse;
import com.caregiver.dto.GeneratedRecipeResponse;
import com.caregiver.entity.ContentTranslation;
import com.caregiver.entity.FoodNutrition;
import com.caregiver.entity.GeneratedRecipe;
import com.caregiver.model.TranslationEntityType;
import com.caregiver.model.TranslationFieldKey;
import com.caregiver.repository.ContentTranslationRepository;
import com.caregiver.util.LocaleResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContentTranslationService {

    private static final Logger log = LoggerFactory.getLogger(ContentTranslationService.class);

    private static final List<String> FOOD_FIELD_KEYS = List.of(
            TranslationFieldKey.FOOD_NAME,
            TranslationFieldKey.REMARK,
            TranslationFieldKey.MEASURE
    );

    private static final List<String> RECIPE_FIELD_KEYS = List.of(
            TranslationFieldKey.RECIPE_TITLE,
            TranslationFieldKey.INGREDIENTS,
            TranslationFieldKey.STEPS,
            TranslationFieldKey.SUITABLE_DESC,
            TranslationFieldKey.UNSUITABLE_DESC,
            TranslationFieldKey.HEALTH_TIP,
            TranslationFieldKey.HIGH_PROTEIN_WARNING
    );

    private static final int LAZY_RECIPE_FILL_CAP = 5;
    private static final int SAVE_CHUNK_SIZE = 50;

    private final Object foodCatalogLock = new Object();

    private final ContentTranslationRepository translationRepository;
    private final TranslationService translationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ContentTranslationService(
            ContentTranslationRepository translationRepository,
            TranslationService translationService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.translationRepository = translationRepository;
        this.translationService = translationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Returns food DTOs in the requested language. English comes from {@code food_nutrition};
     * zh/ms are read from {@code content_translation}, populated via batch backfill when missing.
     */
    public List<FoodNutritionResponse> getLocalizedFoods(List<FoodNutrition> foods, String locale) {
        List<FoodNutritionResponse> dtos = foods.stream().map(FoodNutritionResponse::from).toList();
        if (LocaleResolver.isEnglish(locale) || dtos.isEmpty()) {
            return dtos;
        }
        if (!translationService.isTranslationAvailable()) {
            return dtos;
        }
        if (!isFoodCatalogComplete(foods, locale)) {
            log.info("Populating food catalog translations for locale {}", locale);
            ensureFoodCatalogBatch(foods, locale);
        }
        return applyFoodTranslations(dtos, locale);
    }

    public boolean isFoodCatalogComplete(List<FoodNutrition> foods, String locale) {
        if (LocaleResolver.isEnglish(locale) || foods.isEmpty()) {
            return true;
        }
        long foodsWithNames = foods.stream()
                .filter(f -> f.getFoodName() != null && !f.getFoodName().isBlank())
                .count();
        if (foodsWithNames == 0) {
            return true;
        }
        long translatedNames = translationRepository.countByEntityTypeAndLocaleAndFieldKey(
                TranslationEntityType.FOOD_NUTRITION, locale, TranslationFieldKey.FOOD_NAME
        );
        if (translatedNames < foodsWithNames) {
            return false;
        }

        long foodsWithMeasure = foods.stream()
                .filter(f -> f.getMeasure() != null && !f.getMeasure().isBlank())
                .count();
        if (foodsWithMeasure == 0) {
            return true;
        }
        long translatedMeasures = translationRepository.countByEntityTypeAndLocaleAndFieldKey(
                TranslationEntityType.FOOD_NUTRITION, locale, TranslationFieldKey.MEASURE
        );
        return translatedMeasures >= foodsWithMeasure;
    }

    public List<FoodNutritionResponse> applyFoodTranslations(List<FoodNutritionResponse> dtos, String locale) {
        if (LocaleResolver.isEnglish(locale) || dtos.isEmpty()) {
            return dtos;
        }
        Map<Long, Map<String, String>> byEntity = loadTranslationMap(
                TranslationEntityType.FOOD_NUTRITION,
                dtos.stream().map(FoodNutritionResponse::getId).filter(Objects::nonNull).toList(),
                locale
        );
        for (FoodNutritionResponse dto : dtos) {
            Map<String, String> fields = byEntity.get(dto.getId());
            if (fields == null) {
                continue;
            }
            if (fields.containsKey(TranslationFieldKey.FOOD_NAME)) {
                dto.setFoodName(fields.get(TranslationFieldKey.FOOD_NAME));
            }
            if (fields.containsKey(TranslationFieldKey.REMARK)) {
                dto.setRemark(fields.get(TranslationFieldKey.REMARK));
            }
            if (fields.containsKey(TranslationFieldKey.MEASURE)) {
                dto.setMeasure(fields.get(TranslationFieldKey.MEASURE));
            }
        }
        return dtos;
    }

    public List<GeneratedRecipeResponse> applyRecipeTranslations(List<GeneratedRecipeResponse> dtos, String locale) {
        if (LocaleResolver.isEnglish(locale) || dtos.isEmpty()) {
            return dtos;
        }
        Map<Long, Map<String, String>> byEntity = loadTranslationMap(
                TranslationEntityType.GENERATED_RECIPE,
                dtos.stream().map(GeneratedRecipeResponse::getId).filter(Objects::nonNull).toList(),
                locale
        );
        for (GeneratedRecipeResponse dto : dtos) {
            Map<String, String> fields = byEntity.get(dto.getId());
            if (fields == null) {
                continue;
            }
            applyRecipeFields(dto, fields);
        }
        return dtos;
    }

    /**
     * Lazy-fill missing recipe translations (capped per request), then merge into DTOs.
     */
    public List<GeneratedRecipeResponse> applyRecipeTranslationsWithLazyFill(
            List<GeneratedRecipe> entities,
            String locale
    ) {
        List<GeneratedRecipeResponse> dtos = entities.stream()
                .map(GeneratedRecipeResponse::from)
                .toList();

        if (LocaleResolver.isEnglish(locale) || dtos.isEmpty()) {
            return dtos;
        }

        if (!translationService.isTranslationAvailable()) {
            return dtos;
        }

        List<GeneratedRecipe> needingFill = new ArrayList<>();
        for (GeneratedRecipe entity : entities) {
            if (hasMissingRecipeKeys(entity.getId(), locale)) {
                needingFill.add(entity);
                if (needingFill.size() >= LAZY_RECIPE_FILL_CAP) {
                    break;
                }
            }
        }

        for (GeneratedRecipe entity : needingFill) {
            try {
                ensureRecipeTranslations(entity, Set.of(locale));
            } catch (Exception e) {
                log.warn("Lazy recipe translation failed for id={}: {}", entity.getId(), e.getMessage());
            }
        }

        return applyRecipeTranslations(dtos, locale);
    }

    public void ensureFoodTranslations(FoodNutrition food, Collection<String> locales) {
        Map<String, String> englishFields = foodEnglishFields(food);
        for (String locale : locales) {
            if (LocaleResolver.isEnglish(locale)) {
                continue;
            }
            ensureTranslations(TranslationEntityType.FOOD_NUTRITION, food.getId(), englishFields, locale, FOOD_FIELD_KEYS);
        }
    }

    public void ensureRecipeTranslations(GeneratedRecipe recipe, Collection<String> locales) {
        Map<String, String> englishFields = recipeEnglishFields(recipe);
        for (String locale : locales) {
            if (LocaleResolver.isEnglish(locale)) {
                continue;
            }
            ensureTranslations(
                    TranslationEntityType.GENERATED_RECIPE,
                    recipe.getId(),
                    englishFields,
                    locale,
                    RECIPE_FIELD_KEYS
            );
        }
    }

    @Async
    public void cacheRecipeTranslations(GeneratedRecipe recipe) {
        if (recipe == null || recipe.getId() == null) {
            return;
        }
        try {
            ensureRecipeTranslations(recipe, LocaleResolver.TRANSLATABLE_LOCALES);
        } catch (Exception e) {
            log.warn("Async recipe translation failed for id={}: {}", recipe.getId(), e.getMessage());
        }
    }

    public BackfillResult backfillFoods(List<FoodNutrition> foods, Collection<String> locales) {
        int failures = 0;
        int rowsBefore = 0;
        int rowsAfter = 0;

        for (String locale : locales) {
            if (LocaleResolver.isEnglish(locale)) {
                continue;
            }
            try {
                rowsBefore += (int) translationRepository.countDistinctEntityIdsByEntityTypeAndLocale(
                        TranslationEntityType.FOOD_NUTRITION, locale
                );
                ensureFoodCatalogBatch(foods, locale);
                rowsAfter += (int) translationRepository.countDistinctEntityIdsByEntityTypeAndLocale(
                        TranslationEntityType.FOOD_NUTRITION, locale
                );
            } catch (Exception e) {
                failures++;
                log.warn("Food catalog backfill failed for locale {}: {}", locale, e.getMessage());
            }
        }

        return new BackfillResult(foods.size(), Math.max(0, rowsAfter - rowsBefore), failures);
    }

    /**
     * Batch-translates the static food catalog for one locale and upserts into content_translation.
     * Qwen calls run outside any DB transaction; only short chunked commits touch the database.
     */
    public void ensureFoodCatalogBatch(List<FoodNutrition> foods, String locale) {
        if (LocaleResolver.isEnglish(locale) || foods.isEmpty() || !translationService.isTranslationAvailable()) {
            return;
        }

        synchronized (foodCatalogLock) {
            if (isFoodCatalogComplete(foods, locale)) {
                return;
            }

            log.info("Food catalog translation batch starting for locale {} ({} items)", locale, foods.size());

            List<Long> foodIds = foods.stream().map(FoodNutrition::getId).filter(Objects::nonNull).toList();
            Map<Long, Map<String, ContentTranslation>> existingByFood = loadExistingByEntity(
                    TranslationEntityType.FOOD_NUTRITION, foodIds, locale
            );

            batchTranslateFoodField(foods, locale, TranslationFieldKey.FOOD_NAME, existingByFood,
                    FoodNutrition::getFoodName);

            batchTranslateFoodField(foods, locale, TranslationFieldKey.REMARK, existingByFood,
                    FoodNutrition::getRemark);

            batchTranslateFoodField(foods, locale, TranslationFieldKey.MEASURE, existingByFood,
                    FoodNutrition::getMeasure);

            log.info("Food catalog translation batch finished for locale {}", locale);
        }
    }

    /**
     * Runs food catalog backfill on a background thread so startup does not block the web port.
     */
    @Async
    public void ensureFoodCatalogBatchAsync(List<FoodNutrition> foods, String locale) {
        try {
            ensureFoodCatalogBatch(foods, locale);
        } catch (Exception e) {
            log.warn("Async food catalog translation failed for {}: {}", locale, e.getMessage());
        }
    }

    private void batchTranslateFoodField(
            List<FoodNutrition> foods,
            String locale,
            String fieldKey,
            Map<Long, Map<String, ContentTranslation>> existingByFood,
            java.util.function.Function<FoodNutrition, String> extractor
    ) {
        List<FoodNutrition> pending = new ArrayList<>();
        List<String> englishTexts = new ArrayList<>();

        for (FoodNutrition food : foods) {
            String english = extractor.apply(food);
            if (english == null || english.isBlank()) {
                continue;
            }
            String hash = sha256(english);
            ContentTranslation row = existingByFood
                    .getOrDefault(food.getId(), Map.of())
                    .get(fieldKey);
            if (row != null && hash.equals(row.getSourceHash())) {
                continue;
            }
            pending.add(food);
            englishTexts.add(english);
        }

        if (pending.isEmpty()) {
            return;
        }

        String targetLang = LocaleResolver.toQwenLanguageName(locale);
        List<String> translated = translationService.translateStrings(englishTexts, "English", targetLang);

        List<ContentTranslation> toPersist = new ArrayList<>(pending.size());
        for (int i = 0; i < pending.size(); i++) {
            FoodNutrition food = pending.get(i);
            String english = englishTexts.get(i);
            String value = i < translated.size() ? translated.get(i) : english;
            Map<String, ContentTranslation> perFood = existingByFood.computeIfAbsent(food.getId(), id -> new HashMap<>());
            toPersist.add(prepareTranslationRow(
                    TranslationEntityType.FOOD_NUTRITION,
                    food.getId(),
                    locale,
                    fieldKey,
                    value,
                    sha256(english),
                    perFood
            ));
        }
        saveTranslationsInChunks(toPersist);
    }

    private Map<Long, Map<String, ContentTranslation>> loadExistingByEntity(
            TranslationEntityType entityType,
            List<Long> entityIds,
            String locale
    ) {
        if (entityIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ContentTranslation> rows = translationRepository.findByEntityTypeAndEntityIdInAndLocale(
                entityType, entityIds, locale
        );
        Map<Long, Map<String, ContentTranslation>> result = new HashMap<>();
        for (ContentTranslation row : rows) {
            result.computeIfAbsent(row.getEntityId(), id -> new HashMap<>()).put(row.getFieldKey(), row);
        }
        return result;
    }

    private boolean hasMissingRecipeKeys(Long recipeId, String locale) {
        if (recipeId == null) {
            return false;
        }
        Set<String> existing = translationRepository
                .findByEntityTypeAndEntityIdAndLocale(TranslationEntityType.GENERATED_RECIPE, recipeId, locale)
                .stream()
                .map(ContentTranslation::getFieldKey)
                .collect(Collectors.toSet());
        return existing.size() < RECIPE_FIELD_KEYS.size();
    }

    protected void ensureTranslations(
            TranslationEntityType entityType,
            Long entityId,
            Map<String, String> englishFields,
            String locale,
            List<String> expectedKeys
    ) {
        if (!translationService.isTranslationAvailable()) {
            return;
        }

        List<ContentTranslation> existing = translationRepository.findByEntityTypeAndEntityIdAndLocale(
                entityType, entityId, locale
        );
        Map<String, ContentTranslation> existingByKey = existing.stream()
                .collect(Collectors.toMap(ContentTranslation::getFieldKey, t -> t, (a, b) -> a));

        List<String> scalarKeys = new ArrayList<>();
        List<String> scalarTexts = new ArrayList<>();

        for (String key : expectedKeys) {
            String english = englishFields.get(key);
            if (english == null || english.isBlank()) {
                continue;
            }
            String hash = sha256(english);
            ContentTranslation row = existingByKey.get(key);
            if (row != null && hash.equals(row.getSourceHash())) {
                continue;
            }

            if (isJsonArrayField(key)) {
                saveJsonArrayTranslation(entityType, entityId, locale, key, english, existingByKey, hash);
                continue;
            }

            scalarKeys.add(key);
            scalarTexts.add(english);
        }

        if (scalarKeys.isEmpty()) {
            return;
        }

        String targetLang = LocaleResolver.toQwenLanguageName(locale);
        List<String> translated = translationService.translateStrings(
                scalarTexts, "English", targetLang
        );

        List<ContentTranslation> toPersist = new ArrayList<>(scalarKeys.size());
        for (int i = 0; i < scalarKeys.size(); i++) {
            String key = scalarKeys.get(i);
            String english = englishFields.get(key);
            String translatedValue = i < translated.size() ? translated.get(i) : english;
            toPersist.add(prepareTranslationRow(
                    entityType, entityId, locale, key, translatedValue, sha256(english), existingByKey
            ));
        }
        saveTranslationsInChunks(toPersist);
    }

    private void saveJsonArrayTranslation(
            TranslationEntityType entityType,
            Long entityId,
            String locale,
            String key,
            String englishJson,
            Map<String, ContentTranslation> existingByKey,
            String hash
    ) {
        List<String> items = parseJsonArray(englishJson);
        if (items.isEmpty()) {
            return;
        }
        String targetLang = LocaleResolver.toQwenLanguageName(locale);
        List<String> translatedItems = translationService.translateStrings(items, "English", targetLang);
        try {
            String stored = objectMapper.writeValueAsString(translatedItems);
            ContentTranslation row = prepareTranslationRow(
                    entityType, entityId, locale, key, stored, hash, existingByKey
            );
            saveTranslationsInChunks(List.of(row));
        } catch (Exception e) {
            log.warn("Failed to store translated JSON array for {}: {}", key, e.getMessage());
        }
    }

    private ContentTranslation prepareTranslationRow(
            TranslationEntityType entityType,
            Long entityId,
            String locale,
            String key,
            String translatedText,
            String sourceHash,
            Map<String, ContentTranslation> existingByKey
    ) {
        ContentTranslation row = existingByKey.get(key);
        if (row == null) {
            row = new ContentTranslation();
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setLocale(locale);
            row.setFieldKey(key);
            existingByKey.put(key, row);
        }
        row.setTranslatedText(translatedText);
        row.setSourceHash(sourceHash);
        return row;
    }

    /** Short, chunked commits — never hold locks during Qwen HTTP calls. */
    private void saveTranslationsInChunks(List<ContentTranslation> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (int offset = 0; offset < rows.size(); offset += SAVE_CHUNK_SIZE) {
            int end = Math.min(offset + SAVE_CHUNK_SIZE, rows.size());
            List<ContentTranslation> chunk = new ArrayList<>(rows.subList(offset, end));
            transactionTemplate.executeWithoutResult(status -> translationRepository.saveAll(chunk));
        }
    }

    private boolean isJsonArrayField(String key) {
        return TranslationFieldKey.INGREDIENTS.equals(key) || TranslationFieldKey.STEPS.equals(key);
    }

    private Map<Long, Map<String, String>> loadTranslationMap(
            TranslationEntityType entityType,
            List<Long> entityIds,
            String locale
    ) {
        if (entityIds.isEmpty()) {
            return Map.of();
        }
        List<ContentTranslation> rows = translationRepository.findByEntityTypeAndEntityIdInAndLocale(
                entityType, entityIds, locale
        );
        Map<Long, Map<String, String>> result = new HashMap<>();
        for (ContentTranslation row : rows) {
            result.computeIfAbsent(row.getEntityId(), id -> new HashMap<>())
                    .put(row.getFieldKey(), row.getTranslatedText());
        }
        return result;
    }

    private void applyRecipeFields(GeneratedRecipeResponse dto, Map<String, String> fields) {
        if (fields.containsKey(TranslationFieldKey.RECIPE_TITLE)) {
            dto.setRecipeTitle(fields.get(TranslationFieldKey.RECIPE_TITLE));
        }
        if (fields.containsKey(TranslationFieldKey.INGREDIENTS)) {
            dto.setIngredients(fields.get(TranslationFieldKey.INGREDIENTS));
        }
        if (fields.containsKey(TranslationFieldKey.STEPS)) {
            dto.setSteps(fields.get(TranslationFieldKey.STEPS));
        }
        if (fields.containsKey(TranslationFieldKey.SUITABLE_DESC)) {
            dto.setSuitableDesc(fields.get(TranslationFieldKey.SUITABLE_DESC));
        }
        if (fields.containsKey(TranslationFieldKey.UNSUITABLE_DESC)) {
            dto.setUnsuitableDesc(fields.get(TranslationFieldKey.UNSUITABLE_DESC));
        }
        if (fields.containsKey(TranslationFieldKey.HEALTH_TIP)) {
            dto.setHealthTip(fields.get(TranslationFieldKey.HEALTH_TIP));
        }
        if (fields.containsKey(TranslationFieldKey.HIGH_PROTEIN_WARNING)) {
            dto.setHighProteinWarning(fields.get(TranslationFieldKey.HIGH_PROTEIN_WARNING));
        }
    }

    private Map<String, String> foodEnglishFields(FoodNutrition food) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(TranslationFieldKey.FOOD_NAME, food.getFoodName());
        map.put(TranslationFieldKey.REMARK, food.getRemark());
        map.put(TranslationFieldKey.MEASURE, food.getMeasure());
        return map;
    }

    private Map<String, String> recipeEnglishFields(GeneratedRecipe recipe) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(TranslationFieldKey.RECIPE_TITLE, recipe.getRecipeTitle());
        map.put(TranslationFieldKey.INGREDIENTS, recipe.getIngredients());
        map.put(TranslationFieldKey.STEPS, recipe.getSteps());
        map.put(TranslationFieldKey.SUITABLE_DESC, recipe.getSuitableDesc());
        map.put(TranslationFieldKey.UNSUITABLE_DESC, recipe.getUnsuitableDesc());
        map.put(TranslationFieldKey.HEALTH_TIP, recipe.getHealthTip());
        map.put(TranslationFieldKey.HIGH_PROTEIN_WARNING, recipe.getHighProteinWarning());
        return map;
    }

    private List<String> parseJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String sha256(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public record BackfillResult(int entitiesProcessed, int rowsUpserted, int failures) {}
}
