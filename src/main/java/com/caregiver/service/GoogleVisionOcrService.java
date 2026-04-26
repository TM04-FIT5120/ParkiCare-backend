package com.caregiver.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class GoogleVisionOcrService {

    public String extractText(MultipartFile imageFile) {
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return "";
            }

            ByteString imgBytes = ByteString.copyFrom(imageFile.getBytes());

            Image image = Image.newBuilder()
                    .setContent(imgBytes)
                    .build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.TEXT_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feature)
                    .setImage(image)
                    .build();

            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                BatchAnnotateImagesResponse response =
                        client.batchAnnotateImages(List.of(request));

                AnnotateImageResponse imageResponse =
                        response.getResponses(0);

                if (imageResponse.hasError()) {
                    throw new RuntimeException(imageResponse.getError().getMessage());
                }

                if (imageResponse.getTextAnnotationsList().isEmpty()) {
                    return "";
                }

                return imageResponse
                        .getTextAnnotationsList()
                        .get(0)
                        .getDescription();
            }

        } catch (Exception e) {
            throw new RuntimeException("OCR failed: " + e.getMessage());
        }
    }
}