package com.caregiver.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

public class ImageUploadUtil {

    private static final String IMGBB_UPLOAD_URL =
            "https://api.imgbb.com/1/upload?key=a8992431123391c67cf69cc72791c38b";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String uploadToPublicServer(MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Uploaded image is empty");
        }

        // 1. Convert image to Base64
        String base64Image = Base64.getEncoder().encodeToString(file.getBytes());

        // 2. imgbb needs application/x-www-form-urlencoded
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("image", base64Image);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        // 3. Send request to imgbb
        RestTemplate restTemplate = new RestTemplate();

        String response = restTemplate.postForObject(
                IMGBB_UPLOAD_URL,
                request,
                String.class
        );

        // 4. Parse image URL
        JsonNode root = objectMapper.readTree(response);

        if (!root.has("data") || !root.get("data").has("url")) {
            throw new RuntimeException("Failed to upload image to imgbb: " + response);
        }

        return root.get("data").get("url").asText();
    }
}