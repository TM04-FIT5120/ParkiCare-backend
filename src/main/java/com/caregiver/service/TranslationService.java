package com.caregiver.service;

import com.caregiver.annotation.Translatable;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.*;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    /** Qwen output is unreliable above ~30 items; chunk to avoid truncated JSON arrays. */
    private static final int TRANSLATION_BATCH_SIZE = 25;

    @Value("${qwen.api.key:}")
    private String apiKey;

    @Value("${qwen.api.url}")
    private String qwenUrl;

    @Value("${qwen.api.model:qwen-plus}")
    private String model;

    @Value("${translation.enabled:true}")
    private boolean translationEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long lastRequestTime = 0L;

    private boolean translationActive;

    @PostConstruct
    void initTranslation() {
        translationActive = translationEnabled && apiKey != null && !apiKey.isBlank();
        if (!translationActive) {
            log.warn(
                    "Qwen response/request translation is OFF. Set environment variable QWEN_API_KEY "
                            + "(or qwen.api.key in application-dev.properties) to enable zh/ms catalog translation."
            );
        } else {
            log.info("Qwen translation enabled (model={}).", model);
        }
    }

    public boolean isTranslationAvailable() {
        return translationActive;
    }

    /**
     * Translates a list of strings from one language to another (used by content_translation cache).
     */
    public List<String> translateStrings(List<String> texts, String fromLanguageName, String toLanguageName) {
        if (!translationActive || texts == null || texts.isEmpty()) {
            return texts == null ? List.of() : new ArrayList<>(texts);
        }
        if ("English".equals(fromLanguageName) && "English".equals(toLanguageName)) {
            return new ArrayList<>(texts);
        }
        return translateTextList(texts, fromLanguageName, toLanguageName);
    }

    /**
     * Response 出参翻译：
     * 数据库英文 -> 当前语言
     */
    public void translateObjectToTargetLanguage(Object body, String targetLang) {
        if (!translationActive) {
            return;
        }
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
        if (!translationActive) {
            return;
        }
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
     * 3. 分块调用 Qwen（每块最多 {@link #TRANSLATION_BATCH_SIZE} 条）
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
            log.warn(
                    "Translation size mismatch after batching; keeping originals for {} field(s)",
                    fields.size() - translatedTexts.size()
            );
            translatedTexts = padToSize(originalTexts, translatedTexts);
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
     * Translates a list in fixed-size chunks so Qwen never returns a truncated array.
     */
    private List<String> translateTextList(List<String> texts,
                                           String fromLang,
                                           String toLang) {

        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<String> allTranslated = new ArrayList<>(texts.size());

        for (int offset = 0; offset < texts.size(); offset += TRANSLATION_BATCH_SIZE) {
            int end = Math.min(offset + TRANSLATION_BATCH_SIZE, texts.size());
            List<String> chunk = texts.subList(offset, end);
            allTranslated.addAll(translateTextBatch(chunk, fromLang, toLang));
        }

        return allTranslated;
    }

    /**
     * Single Qwen call for one chunk (at most {@link #TRANSLATION_BATCH_SIZE} strings).
     */
    private List<String> translateTextBatch(List<String> texts,
                                            String fromLang,
                                            String toLang) {

        if (!translationActive) {
            return new ArrayList<>(texts);
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
                - The output array MUST contain exactly %d strings (same as input).
                - Keep the original order.
                - Keep medicine names, numbers, dates, times, dosage, and units unchanged when appropriate.
                - Translate naturally for a mobile app UI.
                - If an item is already in the target language, keep it natural and concise.

                Input JSON array:
                %s
                """.formatted(fromLang, toLang, texts.size(), inputJson);

        Exception lastException = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                waitIfNeeded();

                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model", model);
                requestBody.put("messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are a professional translation engine. Always return valid JSON only."
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ));
                requestBody.put("temperature", 0.1);
                requestBody.put("max_tokens", Math.min(8192, Math.max(1024, texts.size() * 256)));

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
                List<String> translated = parseJsonArray(resultText);

                if (translated.size() == texts.size()) {
                    return translated;
                }

                lastException = new RuntimeException(
                        "Translation result count mismatch. expected="
                                + texts.size()
                                + ", actual="
                                + translated.size()
                );

            } catch (Exception e) {
                lastException = e;
            }

            try {
                Thread.sleep(1000L * attempt);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Translation interrupted", interruptedException);
            }
        }

        // Degrade gracefully: keep English for this chunk instead of failing the API.
        log.warn(
                "Translation batch failed after 3 attempts (size={}): {}",
                texts.size(),
                lastException != null ? lastException.getMessage() : "unknown"
        );
        return new ArrayList<>(texts);
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

    /** Ensures translated list aligns with field count (fallback to originals). */
    private List<String> padToSize(List<String> originals, List<String> translated) {
        List<String> result = new ArrayList<>(originals.size());
        for (int i = 0; i < originals.size(); i++) {
            result.add(i < translated.size() ? translated.get(i) : originals.get(i));
        }
        return result;
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