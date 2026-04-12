package com.caregiver.controller;

import com.caregiver.dto.CaregiverEventOccurrenceResponse;
import com.caregiver.dto.UpsertCaregiverEventOccurrenceRequest;
import com.caregiver.service.CaregiverEventOccurrenceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/caregiver-event-occurrences")
@CrossOrigin(origins = "*")
public class CaregiverEventOccurrenceController {

    private final CaregiverEventOccurrenceService caregiverEventOccurrenceService;

    public CaregiverEventOccurrenceController(CaregiverEventOccurrenceService caregiverEventOccurrenceService) {
        this.caregiverEventOccurrenceService = caregiverEventOccurrenceService;
    }

    @GetMapping
    public List<CaregiverEventOccurrenceResponse> list(
            @RequestParam Long caregiverId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return caregiverEventOccurrenceService.listBetween(caregiverId, from, to);
    }

    @PutMapping
    public CaregiverEventOccurrenceResponse upsert(@Valid @RequestBody UpsertCaregiverEventOccurrenceRequest request) {
        return caregiverEventOccurrenceService.upsert(request);
    }
}
