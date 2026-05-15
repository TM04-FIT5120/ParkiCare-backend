package com.caregiver.service;

import com.caregiver.annotation.Translatable;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.*;

@Service
public class TranslationService {

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.url}")
    private String qwenUrl;

    @Value("${qwen.api.model:qwen-plus}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long lastRequestTime = 0L;

    /**
     * Response 出参翻译：
     * 数据库英文 -> 当前语言
     */
    public void translateObjectToTargetLanguage(Object body, String targetLang) {
        String targetLanguageName = toLanguageName(targetLang);

        if (body == null || "English".equals(targetLanguageName)) {
            return;
        }

        batchTranslateObject(body, "English", targetLanguageName);
    }

    /**
     * Request 入参翻译：
     * 当前语言 -> 英文
     */
    public void translateObjectToEnglish(Object body, String sourceLang) {
        String sourceLanguageName = toLanguageName(sourceLang);

        if (body == null || "English".equals(sourceLanguageName)) {
            return;
        }

        batchTranslateObject(body, sourceLanguageName, "English");
    }

    /**
     * 核心逻辑：
     * 1. 收集所有 @Translatable 字段
     * 2. 组成 List<String>
     * 3. 一次调用 Qwen
     * 4. 解析返回 JSON Array
     * 5. 回填原字段
     */
    private void batchTranslateObject(Object body, String fromLang, String toLang) {
        List<TranslatableField> fields = new ArrayList<>();

        collectTranslatableFields(body, fields);

        if (fields.isEmpty()) {
            return;
        }

        List<String> originalTexts = fields.stream()
                .map(TranslatableField::getValue)
                .toList();

        List<String> translatedTexts = translateTextList(originalTexts, fromLang, toLang);

        if (translatedTexts.size() != fields.size()) {
            throw new RuntimeException(
                    "Translation result count mismatch. expected="
                            + fields.size()
                            + ", actual="
                            + translatedTexts.size()
            );
        }

        for (int i = 0; i < fields.size(); i++) {
            TranslatableField item = fields.get(i);

            try {
                item.getField().setAccessible(true);
                item.getField().set(item.getOwner(), translatedTexts.get(i));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to set translated field", e);
            }
        }
    }

    /**
     * 递归收集所有需要翻译的字段
     */
    private void collectTranslatableFields(Object obj, List<TranslatableField> result) {
        if (obj == null) {
            return;
        }

        if (obj instanceof Map<?, ?>) {
            return;
        }

        if (obj instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectTranslatableFields(item, result);
            }
            return;
        }

        if (isSimpleType(obj.getClass())) {
            return;
        }

        Package pkg = obj.getClass().getPackage();
        if (pkg != null) {
            String packageName = pkg.getName();

            if (packageName.startsWith("java.")
                    || packageName.startsWith("javax.")
                    || packageName.startsWith("jakarta.")
                    || packageName.startsWith("org.springframework.")) {
                return;
            }
        }

        Field[] fields = obj.getClass().getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);

            try {
                Object value = field.get(obj);

                if (value == null) {
                    continue;
                }

                if (field.isAnnotationPresent(Translatable.class)
                        && field.getType().equals(String.class)) {

                    String text = (String) value;

                    if (!text.isBlank()) {
                        result.add(new TranslatableField(obj, field, text));
                    }

                    continue;
                }

                if (value instanceof Collection<?> || !isSimpleType(value.getClass())) {
                    collectTranslatableFields(value, result);
                }

            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to read field: " + field.getName(), e);
            }
        }
    }

    /**
     * 一次把多个字段发给 Qwen
     */
    private List<String> translateTextList(List<String> texts,
                                           String fromLang,
                                           String toLang) {

        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        String inputJson = toJson(texts);

        String prompt = """
                You are a translation engine.

                Translate each item in the JSON array from %s to %s.

                Rules:
                - Return ONLY a valid JSON array of strings.
                - Do NOT return markdown.
                - Do NOT use code fences.
                - Do NOT explain.
                - The output array size must be exactly the same as the input array size.
                - Keep the original order.
                - Keep medicine names, numbers, dates, times, dosage, and units unchanged when appropriate.
                - Translate naturally for a mobile app UI.
                - If an item is already in the target language, keep it natural and concise.

                Input JSON array:
                %s
                """.formatted(fromLang, toLang, inputJson);

        Exception lastException = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                waitIfNeeded();

                Map<String, Object> requestBody = Map.of(
                        "model", model,
                        "messages", List.of(
                                Map.of(
                                        "role", "system",
                                        "content", "You are a professional translation engine. Always return valid JSON only."
                                ),
                                Map.of(
                                        "role", "user",
                                        "content", prompt
                                )
                        ),
                        "temperature", 0.1
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey);

                HttpEntity<Map<String, Object>> entity =
                        new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(
                        qwenUrl,
                        entity,
                        Map.class
                );

                String resultText = extractQwenText(response.getBody());

                return parseJsonArray(resultText);

            } catch (Exception e) {
                lastException = e;

                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Translation interrupted", interruptedException);
                }
            }
        }

        throw new RuntimeException("Qwen translation failed after 3 attempts", lastException);
    }

    /**
     * 从 Qwen response 里取 content
     *
     * Qwen OpenAI-compatible response:
     * choices[0].message.content
     */
    private String extractQwenText(Map responseBody) {
        if (responseBody == null) {
            throw new RuntimeException("Qwen response is null");
        }

        Object choicesObj = responseBody.get("choices");

        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new RuntimeException("Qwen choices is empty: " + responseBody);
        }

        Object firstChoice = choices.get(0);

        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            throw new RuntimeException("Invalid Qwen choice: " + firstChoice);
        }

        Object messageObj = choiceMap.get("message");

        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            throw new RuntimeException("Invalid Qwen message: " + messageObj);
        }

        Object contentObj = messageMap.get("content");

        if (contentObj == null) {
            throw new RuntimeException("Qwen content is null: " + messageMap);
        }

        return contentObj.toString().trim();
    }

    /**
     * Qwen 返回 JSON Array，例如：
     * ["用药提醒", "早餐后服用"]
     */
    private List<String> parseJsonArray(String text) {
        try {
            String cleaned = text.trim();

            if (cleaned.startsWith("```")) {
                cleaned = cleaned
                        .replace("```json", "")
                        .replace("```", "")
                        .trim();
            }

            int start = cleaned.indexOf("[");
            int end = cleaned.lastIndexOf("]");

            if (start >= 0 && end >= start) {
                cleaned = cleaned.substring(start, end + 1);
            }

            return objectMapper.readValue(
                    cleaned,
                    new TypeReference<List<String>>() {}
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to parse Qwen translation result: " + text,
                    e
            );
        }
    }

    private String toJson(List<String> texts) {
        try {
            return objectMapper.writeValueAsString(texts);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert text list to JSON", e);
        }
    }

    /**
     * 简单限流，避免短时间调用太快
     */
    private synchronized void waitIfNeeded() {
        long now = System.currentTimeMillis();
        long diff = now - lastRequestTime;

        if (diff < 1100) {
            try {
                Thread.sleep(1100 - diff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Translation interrupted", e);
            }
        }

        lastRequestTime = System.currentTimeMillis();
    }

    /**
     * 前端传：
     * en / zh-CN / ms-MY
     */
    private String toLanguageName(String lang) {
        if (lang == null || lang.isBlank()) {
            return "English";
        }

        lang = lang.trim().toLowerCase();

        if (lang.startsWith("zh")) {
            return "Simplified Chinese";
        }

        if (lang.startsWith("ms")) {
            return "Malay";
        }

        if (lang.startsWith("en")) {
            return "English";
        }

        return "English";
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.equals(String.class)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class.equals(clazz)
                || Character.class.equals(clazz)
                || Date.class.isAssignableFrom(clazz)
                || clazz.getName().startsWith("java.time")
                || clazz.isEnum();
    }

    private static class TranslatableField {

        private final Object owner;
        private final Field field;
        private final String value;

        public TranslatableField(Object owner, Field field, String value) {
            this.owner = owner;
            this.field = field;
            this.value = value;
        }

        public Object getOwner() {
            return owner;
        }

        public Field getField() {
            return field;
        }

        public String getValue() {
            return value;
        }
    }
}