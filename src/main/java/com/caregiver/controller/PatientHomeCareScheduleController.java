package com.caregiver.controller;

import com.caregiver.dto.HomeCareScheduleRequest;
import com.caregiver.dto.HomeCareScheduleResponse;
import com.caregiver.service.PatientHomeCareScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/home-care")
@CrossOrigin(origins = "*")
public class PatientHomeCareScheduleController {

    private final PatientHomeCareScheduleService service;

    public PatientHomeCareScheduleController(PatientHomeCareScheduleService service) {
        this.service = service;
    }

    @PostMapping
    public HomeCareScheduleResponse create(@Valid @RequestBody HomeCareScheduleRequest request) {
        return service.create(request);
    }

    @GetMapping("/patient/{patientId}")
    public List<HomeCareScheduleResponse> getByPatient(@PathVariable Long patientId) {
        return service.getByPatient(patientId);
    }

    @PutMapping("/{id}")
    public HomeCareScheduleResponse update(@PathVariable Long id,
                                           @Valid @RequestBody HomeCareScheduleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "Home care event deleted successfully";
    }
}