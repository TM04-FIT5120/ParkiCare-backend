package com.caregiver.controller;

import com.caregiver.dto.CaregiverScheduleRequest;
import com.caregiver.dto.CaregiverScheduleResponse;
import com.caregiver.service.CaregiverScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/caregiver-schedule")
@CrossOrigin(origins = "*")
public class CaregiverScheduleController {

    private final CaregiverScheduleService caregiverScheduleService;

    public CaregiverScheduleController(CaregiverScheduleService caregiverScheduleService) {
        this.caregiverScheduleService = caregiverScheduleService;
    }

    @PostMapping
    public CaregiverScheduleResponse createSchedule(@Valid @RequestBody CaregiverScheduleRequest request) {
        return caregiverScheduleService.createSchedule(request);
    }

    @GetMapping("/caregiver/{caregiverId}")
    public List<CaregiverScheduleResponse> getSchedulesByCaregiver(@PathVariable Long caregiverId) {
        return caregiverScheduleService.getSchedulesByCaregiver(caregiverId);
    }

    @GetMapping("/{id}")
    public CaregiverScheduleResponse getScheduleById(@PathVariable Long id) {
        return caregiverScheduleService.getScheduleById(id);
    }

    @PutMapping("/{id}")
    public CaregiverScheduleResponse updateSchedule(@PathVariable Long id,
                                                    @Valid @RequestBody CaregiverScheduleRequest request) {
        return caregiverScheduleService.updateSchedule(id, request);
    }

    @DeleteMapping("/{id}")
    public String deleteSchedule(@PathVariable Long id) {
        caregiverScheduleService.deleteSchedule(id);
        return "Caregiver schedule deleted successfully";
    }
}