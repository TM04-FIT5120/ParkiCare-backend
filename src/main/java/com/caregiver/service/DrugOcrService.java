package com.caregiver.service;


import com.caregiver.dto.DrugOcrVO;
import com.caregiver.util.ImageUploadUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
public class DrugOcrService {

    private static final String QWEN_API_KEY = "sk-19d91080507147f1ac178c93e8936aa6";

    private static final String QWEN_OCR_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final RestTemplate restTemplate = new RestTemplate();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DrugOcrVO recognizeDrug(MultipartFile file) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No image uploaded");
        }

        // 1. 上传图片到公共图床，拿到 imageUrl
        String imageUrl = ImageUploadUtil.uploadToPublicServer(file);

        // 2. 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + QWEN_API_KEY);

        // 3. system message
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put(
                "content",
                "你是专业药品OCR识别器。只输出纯JSON，无任何多余内容。" +
                        "固定返回JSON结构：{\"medicineName\":\"药品名\",\"quantity\":\"容量\",\"manufacturer\":\"生产商\"}。" +
                        "识别不到的值必须设为null，不能是空字符串，不能有换行符。"
        );

        // 4. user message content
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "识别图片中的药品信息，严格按指定JSON结构返回");

        Map<String, Object> imageUrlObj = new HashMap<>();
        imageUrlObj.put("url", imageUrl);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", imageUrlObj);

        Object[] userContent = new Object[]{textPart, imagePart};

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        // 5. 请求体
        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");

        Map<String, Object> body = new HashMap<>();
        body.put("model", "qwen-vl-ocr");
        body.put("temperature", 0.0);
        body.put("max_tokens", 1024);
        body.put("response_format", responseFormat);
        body.put("messages", new Object[]{systemMsg, userMsg});

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        // 6. 调用 Qwen OCR
        ResponseEntity<String> response = restTemplate.postForEntity(
                QWEN_OCR_URL,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Image recognition encountered an issue. Please enter the information manually.");
        }

        // 7. 解析 Qwen 返回
        JsonNode root = objectMapper.readTree(response.getBody());

        String content = root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

        content = content
                .replace("```json", "")
                .replace("```", "")
                .trim();

        // 8. 转成前端需要的 VO
        return objectMapper.readValue(content, DrugOcrVO.class);
    }
}