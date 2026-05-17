package com.caregiver.controller;

import com.caregiver.dto.UpdateLanguageRequest;
import com.caregiver.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/caregiver")
@CrossOrigin(origins = "*")
public class CaregiverController {

    private final AuthService authService;

    public CaregiverController(AuthService authService) {
        this.authService = authService;
    }

    @PatchMapping("/{id}/language")
    public ResponseEntity<Void> updateLanguage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLanguageRequest request) {
        authService.updateLanguage(id, request.getLanguage());
        return ResponseEntity.noContent().build();
    }
}
