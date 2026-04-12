package com.caregiver.controller;

import com.caregiver.dto.OutdoorScheduleRequest;
import com.caregiver.dto.OutdoorScheduleResponse;
import com.caregiver.service.PatientOutdoorScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/outdoor")
@CrossOrigin(origins = "*")
public class PatientOutdoorScheduleController {

    private final PatientOutdoorScheduleService service;

    public PatientOutdoorScheduleController(PatientOutdoorScheduleService service) {
        this.service = service;
    }

    @PostMapping
    public OutdoorScheduleResponse create(@Valid @RequestBody OutdoorScheduleRequest request) {
        return service.create(request);
    }

    @GetMapping("/patient/{patientId}")
    public List<OutdoorScheduleResponse> getByPatient(@PathVariable Long patientId) {
        return service.getByPatient(patientId);
    }

    @PutMapping("/{id}")
    public OutdoorScheduleResponse update(@PathVariable Long id,
                                          @Valid @RequestBody OutdoorScheduleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id, @RequestParam Long caregiverId) {
        service.delete(id, caregiverId);
        return "Outdoor event deleted successfully";
    }
}