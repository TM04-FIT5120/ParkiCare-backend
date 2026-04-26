package com.caregiver.service;

import com.caregiver.dto.ApiResponse;
import com.caregiver.dto.MedicineRecognitionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MedicineRecognitionService {

    private final GoogleVisionOcrService googleVisionOcrService;
    private final GeminiMedicineParserService geminiMedicineParserService;

    public MedicineRecognitionService(
            GoogleVisionOcrService googleVisionOcrService,
            GeminiMedicineParserService geminiMedicineParserService
    ) {
        this.googleVisionOcrService = googleVisionOcrService;
        this.geminiMedicineParserService = geminiMedicineParserService;
    }

    public ApiResponse<MedicineRecognitionResponse> recognizeMedicineBox(MultipartFile image) {

        if (image == null || image.isEmpty()) {
            return ApiResponse.fail("No image uploaded. Please upload a medicine package image.");
        }

        String rawText = googleVisionOcrService.extractText(image);

        if (rawText == null || rawText.trim().isEmpty()) {
            return ApiResponse.fail("No readable text detected. Please upload a clearer image.");
        }

        MedicineRecognitionResponse result = geminiMedicineParserService.parseMedicineText(rawText);

        if (isInvalidRecognition(result)) {
            return ApiResponse.fail("Unable to extract medicine information. Please upload a valid medicine package image.");
        }

        return ApiResponse.success("Recognition successful", result);
    }

    private boolean isInvalidRecognition(MedicineRecognitionResponse result) {
        if (result == null) {
            return true;
        }

        boolean noDrugName = result.getDrugName() == null || result.getDrugName().isBlank();
        boolean noDosage = result.getDosage() == null || result.getDosage().isBlank();
        boolean noManufacturer = result.getManufacturer() == null || result.getManufacturer().isBlank();

        return noDrugName && noDosage && noManufacturer;
    }
}