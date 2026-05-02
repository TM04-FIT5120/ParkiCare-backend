package com.caregiver.service;

import com.caregiver.dto.RecipeGenerateRequest;
import com.caregiver.entity.FoodNutrition;
import com.caregiver.repository.FoodNutritionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecipeGenerateService {

    private static final String QWEN_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private static final String API_KEY =
            "sk-19d91080507147f1ac178c93e8936aa6";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final FoodNutritionRepository foodNutritionRepository;

    public String generateRecipe(RecipeGenerateRequest request) {

        if (request == null || request.getFoods() == null || request.getFoods().isEmpty()) {
            throw new RuntimeException("Food list cannot be empty");
        }

        List<String> foods = request.getFoods();

        boolean hasHighProtein = hasHighProteinFood(foods);

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

            return root
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content")
                    .asText();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate recipe: " + e.getMessage());
        }
    }

    private boolean hasHighProteinFood(List<String> foods) {

        List<FoodNutrition> foodList = foodNutritionRepository.findByFoodNameIn(foods);

        return foodList.stream().anyMatch(food ->
                "Caution".equalsIgnoreCase(food.getSafetyStatus())
                        || food.getProtein100g().compareTo(BigDecimal.valueOf(12)) > 0
        );
    }

    private String buildPrompt(List<String> foods, boolean hasHighProtein) {

        String foodList = String.join(", ", foods);

        String warningInstruction = hasHighProtein
                ? "The selected basket contains high-protein or caution food. " +
                "You must include this exact warning in healthTip: " +
                "\"This meal contains high protein. To ensure Levodopa efficacy, serve 1 hour after medication.\" "
                : "The selected basket does not contain high-protein caution food, so do not add a Levodopa protein warning unless necessary. ";

        return "Use the following ingredients to generate 1 Parkinson-friendly recipe: "
                + foodList
                + ". "
                + warningInstruction
                + "The content must be in English. "
                + "Use only the provided ingredients as the main ingredients. "
                + "You may use basic cooking materials such as water, a very small amount of oil, herbs, or mild seasoning if needed. "
                + "The recipe should remain low sugar, low salt, and low saturated fat. "
                + "Avoid high-salt, high-sugar, deep-fried, or high-saturated-fat cooking methods. "
                + "Prefer simple cooking methods such as steaming, boiling, blending, baking, or light stir-frying. "
                + "The recipe should match Malaysian local food preferences and common home-cooking styles. "
                + "Prefer simple, familiar Malaysian-style dishes such as soup, porridge (congee), steamed dishes, clear broth, rice-based meals, or light stir-fry. "
                + "Avoid overly Western-style dishes unless the selected ingredients clearly fit that style. "
                + "Use mild and common Malaysian seasonings such as garlic, ginger, spring onion, turmeric, or a small amount of light soy sauce. "
                + "Avoid spicy, oily, deep-fried, or coconut-milk-heavy dishes to keep it suitable for elderly patients. "
                + "The recipe should be practical and easy for Malaysian family caregivers to cook at home. "
                + "The recipe should be suitable for elderly Parkinson's patients and family caregivers. "
                + "Do not provide medical diagnosis or treatment advice. "
                + "Write caregiver-friendly explanations. "
                + "Strictly return the result using this JSON structure only: "
                + "{"
                + "\"recipes\":["
                + "{"
                + "\"recipeTitle\":\"Recipe name\","
                + "\"ingredients\":[\"ingredient 1\",\"ingredient 2\"],"
                + "\"steps\":[\"step 1\",\"step 2\",\"step 3\"],"
                + "\"suitableDesc\":\"Explain why this recipe is suitable for Parkinson patients\","
                + "\"unsuitableDesc\":\"Explain who should avoid or be careful with this recipe\","
                + "\"healthTip\":\"Health tip for Parkinson caregivers\""
                + "}"
                + "]"
                + "}";
    }
}