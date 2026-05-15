package com.caregiver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class QwenChatClient {

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Send one prompt to Qwen and return raw JSON text.
    public String generateJsonByPrompt(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = Map.of(
                    "model", "qwen-flash",
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.2,
                    "max_tokens", 2000,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "你只能输出纯粹的JSON文本，禁止任何Markdown、```符号、注释、多余解释文字，不要有多余换行，输出开头必须是{，结尾必须是}。严格按照指定JSON字段结构返回，不新增字段、不缺字段。"
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    )
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText();
            return content.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            throw new RuntimeException("Failed to call qwen api: " + e.getMessage());
        }
    }
}
