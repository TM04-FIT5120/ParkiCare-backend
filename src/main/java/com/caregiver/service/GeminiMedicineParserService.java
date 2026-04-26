package com.caregiver.service;

import com.caregiver.dto.MedicineRecognitionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GeminiMedicineParserService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MedicineRecognitionResponse parseMedicineText(String rawText) {
        try {
            String prompt = """
                    You are a medication information extraction assistant.

                    The following text is extracted from a medicine package by OCR.
                    Extract useful medicine information.

                    Return ONLY valid JSON.
                    Do not include markdown.
                    If a field is unclear, return null.
                    Do not guess too much.
                    confidence must be between 0 and 1.

                    Required JSON format:
                    {
                      "drugName": "",
                      "dosage": "",
                      "manufacturer": "",
                      "form": "",
                      "quantity": "",
                      "rawText": "",
                      "confidence": 0.0
                    }

                    OCR text:
                    """ + rawText;

            Map<String, Object> requestBody = Map.of(
                    "contents", new Object[]{
                            Map.of(
                                    "parts", new Object[]{
                                            Map.of("text", prompt)
                                    }
                            )
                    }
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            geminiApiUrl,
                            request,
                            String.class
                    );

            JsonNode root = objectMapper.readTree(response.getBody());

            String aiText = root
                    .path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            aiText = aiText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();

            MedicineRecognitionResponse result =
                    objectMapper.readValue(aiText, MedicineRecognitionResponse.class);

            result.setRawText(rawText);

            return result;

        } catch (Exception e) {
            throw new RuntimeException("Gemini parsing failed: " + e.getMessage());
        }
    }
}