package com.caregiver.controller;

import com.caregiver.dto.EventRecommendationRequest;
import com.caregiver.dto.EventRecommendationResponse;
import com.caregiver.dto.RecommendationFeedbackCreateRequest;
import com.caregiver.dto.RecommendationFeedbackResponse;
import com.caregiver.service.EventRecommendationService;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eventRecommendation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EventRecommendationController {

    private final EventRecommendationService eventRecommendationService;
    private final Validator validator;

    // Generate 5 AI event recommendations.
    @PostMapping("/generate")
    public EventRecommendationResponse generate(@Valid @RequestBody EventRecommendationRequest request) {
        return eventRecommendationService.generateRecommendations(request);
    }

    // Insert one feedback-learning log when user accepts or rejects.
    @PostMapping("/feedback")
    public ResponseEntity<RecommendationFeedbackResponse> createFeedback(
            @RequestBody RecommendationFeedbackCreateRequest request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String msg = violations.iterator().next().getMessage();
            return ResponseEntity.badRequest().body(RecommendationFeedbackResponse.fail(msg));
        }
        try {
            eventRecommendationService.createFeedbackLog(request);
            return ResponseEntity.ok(RecommendationFeedbackResponse.okay());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(RecommendationFeedbackResponse.fail(e.getMessage()));
        }
    }
}
