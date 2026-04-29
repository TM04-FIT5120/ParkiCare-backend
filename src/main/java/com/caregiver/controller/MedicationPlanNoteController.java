package com.caregiver.controller;

import com.caregiver.dto.MedicationPlanNoteRequest;
import com.caregiver.dto.MedicationPlanNoteResponse;
import com.caregiver.service.MedicationPlanNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reminder/plan-note")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MedicationPlanNoteController {

    private final MedicationPlanNoteService medicationPlanNoteService;

    @GetMapping("/{remindId}")
    public MedicationPlanNoteResponse getPlanNote(
            @PathVariable Long remindId,
            @RequestParam Long caregiverId
    ) {
        return medicationPlanNoteService.getPlanNote(remindId, caregiverId);
    }

    @PostMapping("/{remindId}")
    public MedicationPlanNoteResponse createPlanNote(
            @PathVariable Long remindId,
            @RequestParam Long caregiverId,
            @Valid @RequestBody MedicationPlanNoteRequest request
    ) {
        return medicationPlanNoteService.createOrUpdatePlanNote(remindId, caregiverId, request.getPlanNote());
    }

    @PatchMapping("/{remindId}")
    public MedicationPlanNoteResponse updatePlanNote(
            @PathVariable Long remindId,
            @RequestParam Long caregiverId,
            @Valid @RequestBody MedicationPlanNoteRequest request
    ) {
        return medicationPlanNoteService.createOrUpdatePlanNote(remindId, caregiverId, request.getPlanNote());
    }

    @DeleteMapping("/{remindId}")
    public MedicationPlanNoteResponse deletePlanNote(
            @PathVariable Long remindId,
            @RequestParam Long caregiverId
    ) {
        return medicationPlanNoteService.deletePlanNote(remindId, caregiverId);
    }
}

