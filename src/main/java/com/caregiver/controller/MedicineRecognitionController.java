package com.caregiver.controller;


import com.caregiver.dto.ApiResponse;
import com.caregiver.dto.MedicineRecognitionResponse;
import com.caregiver.service.MedicineRecognitionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
        import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/recognize")
@CrossOrigin(origins = "*")
public class MedicineRecognitionController {

    private final MedicineRecognitionService medicineRecognitionService;

    public MedicineRecognitionController(MedicineRecognitionService medicineRecognitionService) {
        this.medicineRecognitionService = medicineRecognitionService;
    }

    @PostMapping("/uploadPic")
    public ResponseEntity<ApiResponse<MedicineRecognitionResponse>> recognizeMedicineBox(
            @RequestParam("image") MultipartFile image
    ) {
        ApiResponse<MedicineRecognitionResponse> response =
                medicineRecognitionService.recognizeMedicineBox(image);

        return ResponseEntity.ok(response);
    }
}