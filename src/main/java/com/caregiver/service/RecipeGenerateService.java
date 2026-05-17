package com.caregiver.service;

import com.caregiver.dto.RecipeGenerateRequest;
import com.caregiver.entity.FoodNutrition;
import com.caregiver.entity.GeneratedRecipe;
import com.caregiver.repository.FoodNutritionRepository;
import com.caregiver.repository.GeneratedRecipeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeGenerateService {

    private static final String QWEN_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private static final String API_KEY =
            "sk-19d91080507147f1ac178c93e8936aa6";

    private static final String HIGH_PROTEIN_WARNING =
            "This meal contains high protein. To ensure Levodopa efficacy, serve 1 hour after medication.";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final FoodNutritionRepository foodNutritionRepository;
    private final GeneratedRecipeRepository generatedRecipeRepository;
    private final ContentTranslationService contentTranslationService;

    public Map<String, Object> generateRecipe(RecipeGenerateRequest request) {

        if (request == null || request.getFoods() == null || request.getFoods().isEmpty()) {
            throw new RuntimeException("Food list cannot be empty");
        }

        List<String> foods = request.getFoods();
        Long caregiverId = request.getCaregiverId();

        boolean hasHighProtein = hasHighProteinFood(foods);
        String referenceSource = hasHighProtein ? getHighProteinSources(foods) : null;
        Set<String> highProteinFoodNames = hasHighProtein ? getHighProteinFoodNames(foods) : Collections.emptySet();

        try {
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(API_KEY);

            Map<String, Object> body = Map.of(
                    "model", "qwen-flash",
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.2,
                    "max_tokens", 1000,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content",
                                    "You are a recipe generation assistant for Parkinson's caregivers. " +
                                            "You must only output pure valid JSON. " +
                                            "Do not output Markdown. " +
                                            "Do not output code blocks. " +
                                            "Do not output explanations outside JSON. " +
                                            "Do not output comments. " +
                                            "The output must start with { and end with }. " +
                                            "All text content inside JSON must be in English."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", buildPrompt(foods, hasHighProtein)
                            )
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    QWEN_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            String aiContent = root
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

            JsonNode aiRoot = objectMapper.readTree(aiContent);
            JsonNode recipesNode = aiRoot.path("recipes");

            // Hard cap: never return more than 2 recipes regardless of what the AI produced.
            if (recipesNode.isArray() && recipesNode.size() > 2) {
                ArrayNode capped = objectMapper.createArrayNode();
                capped.add(recipesNode.get(0));
                capped.add(recipesNode.get(1));
                ((com.fasterxml.jackson.databind.node.ObjectNode) aiRoot).set("recipes", capped);
                aiContent = objectMapper.writeValueAsString(aiRoot);
                recipesNode = capped;
            }

            persistRecipes(aiContent, foods, caregiverId, hasHighProtein, referenceSource, highProteinFoodNames);

            return Map.of(
                    "recipes", recipesNode,
                    "highProteinWarning", hasHighProtein ? HIGH_PROTEIN_WARNING : "",
                    "referenceSource", referenceSource != null ? referenceSource : ""
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate recipe: " + e.getMessage());
        }
    }

    private void persistRecipes(String aiContent, List<String> foods, Long caregiverId,
                                boolean hasHighProtein, String referenceSource,
                                Set<String> highProteinFoodNames) {
        try {
            JsonNode recipesNode = objectMapper.readTree(aiContent).path("recipes");
            String inputFoods = String.join(", ", foods);

            for (JsonNode recipeNode : recipesNode) {
                // Only warn if this specific recipe's ingredient list contains a high-protein food.
                boolean recipeHasHighProtein = false;
                if (hasHighProtein && !highProteinFoodNames.isEmpty()) {
                    for (JsonNode ingNode : recipeNode.path("ingredients")) {
                        String ing = ingNode.asText().toLowerCase();
                        if (highProteinFoodNames.stream().anyMatch(f -> ing.contains(f.toLowerCase()))) {
                            recipeHasHighProtein = true;
                            break;
                        }
                    }
                }

                GeneratedRecipe recipe = new GeneratedRecipe();
                recipe.setCaregiverId(caregiverId);
                recipe.setInputFoods(inputFoods);
                recipe.setRecipeTitle(recipeNode.path("recipeTitle").asText());
                recipe.setIngredients(recipeNode.path("ingredients").toString());
                recipe.setSteps(recipeNode.path("steps").toString());
                recipe.setSuitableDesc(recipeNode.path("suitableDesc").asText());
                recipe.setUnsuitableDesc(recipeNode.path("unsuitableDesc").asText());
                recipe.setHealthTip(recipeNode.path("healthTip").asText());
                recipe.setCategory(recipeNode.path("category").asText("MAIN"));
                recipe.setHighProteinWarning(recipeHasHighProtein ? HIGH_PROTEIN_WARNING : null);
                recipe.setReferenceSource(recipeHasHighProtein ? referenceSource : null);
                GeneratedRecipe saved = generatedRecipeRepository.save(recipe);
                contentTranslationService.cacheRecipeTranslations(saved);
            }
        } catch (Exception e) {
            // Persistence failure should not block the response
        }
    }

    private boolean hasHighProteinFood(List<String> foods) {
        List<FoodNutrition> foodList = foodNutritionRepository.findByFoodNameIn(foods);
        return foodList.stream().anyMatch(food ->
                "Caution".equalsIgnoreCase(food.getSafetyStatus())
                        || food.getProtein100g().compareTo(BigDecimal.valueOf(12)) > 0
        );
    }

    private String getHighProteinSources(List<String> foods) {
        List<FoodNutrition> foodList = foodNutritionRepository.findByFoodNameIn(foods);
        return foodList.stream()
                .filter(food -> "Caution".equalsIgnoreCase(food.getSafetyStatus())
                        || food.getProtein100g().compareTo(BigDecimal.valueOf(12)) > 0)
                .map(FoodNutrition::getSource)
                .filter(source -> source != null && !source.isBlank())
                .distinct()
                .collect(Collectors.joining("; "));
    }

    private Set<String> getHighProteinFoodNames(List<String> foods) {
        List<FoodNutrition> foodList = foodNutritionRepository.findByFoodNameIn(foods);
        return foodList.stream()
                .filter(food -> "Caution".equalsIgnoreCase(food.getSafetyStatus())
                        || food.getProtein100g().compareTo(BigDecimal.valueOf(12)) > 0)
                .map(FoodNutrition::getFoodName)
                .collect(Collectors.toSet());
    }

    private String buildPrompt(List<String> foods, boolean hasHighProtein) {

        String foodList = String.join(", ", foods);

        String warningInstruction = hasHighProtein
                ? "The selected basket contains high-protein or caution food. " +
                "You must include this exact warning in healthTip: " +
                "\"This meal contains high protein. To ensure Levodopa efficacy, serve 1 hour after medication.\" "
                : "The selected basket does not contain high-protein caution food, so do not add a Levodopa protein warning unless necessary. ";

        return "Use the following ingredients to generate a Parkinson-friendly meal plan: "
                + foodList
                + ". "
                + warningInstruction
                + "The content must be in English. "
                + "Use only the provided ingredients as the main ingredients. "
                + "You may use basic cooking materials such as water, a very small amount of oil, herbs, or mild seasoning if needed. Mark them as optional. "
                + "IMPORTANT: The recipes array must contain a MAXIMUM of 2 recipes total. Never return more than 2 recipes under any circumstances. "
                + "The first recipe must always be the MAIN dish (category: \"MAIN\") using the primary protein and carbohydrate ingredients. "
                + "If the provided ingredients include fruits, light foods, or items naturally suited for a side dish, dessert, or snack, also generate exactly one additional item with the appropriate category: \"SIDE\", \"DESSERT\", or \"SNACK\". "
                + "If all ingredients fit together in one main dish, or if there is not one clearly distinct light/fruit ingredient left over, generate only 1 recipe with category \"MAIN\". "
                + "Do not generate separate recipes for similar ingredients — combine them. The total recipe count must be 1 or 2, never 3 or more. "
                + "Each item in the recipes array must include a \"category\" field. Valid values are: \"MAIN\", \"SIDE\", \"DESSERT\", \"SNACK\". "
                + "The recipes should remain low sugar, low salt, and low saturated fat. "
                + "Avoid high-salt, high-sugar, deep-fried, or high-saturated-fat cooking methods. "
                + "Prefer simple cooking methods such as steaming, boiling, blending, baking, or light stir-frying. "
                + "The recipes should match Malaysian local food preferences and common home-cooking styles. "
                + "Prefer simple, familiar Malaysian-style dishes such as soup, porridge (congee), steamed dishes, clear broth, rice-based meals, or light stir-fry. "
                + "Avoid overly Western-style dishes unless the selected ingredients clearly fit that style. "
                + "Use mild and common Malaysian seasonings such as garlic, ginger, spring onion, and/or turmeric. "
                + "Avoid spicy, oily, deep-fried, or coconut-milk-heavy dishes to keep it suitable for elderly patients. "
                + "The recipes should be practical and easy for Malaysian family caregivers to cook at home. "
                + "The recipes should be suitable for elderly Parkinson's patients and family caregivers. "
                + "Do not provide medical diagnosis or treatment advice. "
                + "Write caregiver-friendly explanations. "
                + "Strictly return the result using this JSON structure only. "
                + "When generating only a main dish, return one object in the recipes array. "
                + "When generating a main dish plus a side or dessert, return two objects with different category values as shown below: "
                + "{"
                + "\"recipes\":["
                + "{"
                + "\"recipeTitle\":\"Main dish name\","
                + "\"category\":\"MAIN\","
                + "\"ingredients\":[\"ingredient 1\",\"ingredient 2\"],"
                + "\"steps\":[\"step 1\",\"step 2\",\"step 3\"],"
                + "\"suitableDesc\":\"Explain why this recipe is suitable for Parkinson patients\","
                + "\"unsuitableDesc\":\"Explain who should avoid or be careful with this recipe\","
                + "\"healthTip\":\"Health tip for Parkinson caregivers\""
                + "},"
                + "{"
                + "\"recipeTitle\":\"Side dish or dessert name\","
                + "\"category\":\"SIDE\","
                + "\"ingredients\":[\"ingredient 1\",\"ingredient 2\"],"
                + "\"steps\":[\"step 1\",\"step 2\"],"
                + "\"suitableDesc\":\"Explain why this recipe is suitable for Parkinson patients\","
                + "\"unsuitableDesc\":\"Explain who should avoid or be careful with this recipe\","
                + "\"healthTip\":\"Health tip for Parkinson caregivers\""
                + "}"
                + "]"
                + "}";
    }
}
