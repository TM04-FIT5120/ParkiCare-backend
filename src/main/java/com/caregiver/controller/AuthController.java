package com.caregiver.controller;

import com.caregiver.dto.LoginRequest;
import com.caregiver.dto.LoginResponse;
import com.caregiver.dto.RegisterRequest;
import com.caregiver.dto.RegisterResponse;
import com.caregiver.dto.UpdateLanguageRequest;
import com.caregiver.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse loginName(@Valid @RequestBody LoginRequest request) {
        return authService.loginName(request);
    }

    /** Persist UI language choice (also exposed under /api/caregiver/{id}/language). */
    @PatchMapping("/caregiver/{id}/language")
    public ResponseEntity<Void> updateLanguage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLanguageRequest request) {
        authService.updateLanguage(id, request.getLanguage());
        return ResponseEntity.noContent().build();
    }
}