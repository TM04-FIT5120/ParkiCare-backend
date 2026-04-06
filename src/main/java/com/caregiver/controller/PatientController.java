package com.caregiver.controller;

import com.caregiver.dto.PatientRequest;
import com.caregiver.dto.PatientResponse;
import com.caregiver.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@CrossOrigin(origins = "*")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    // 新增患者
    @PostMapping
    public PatientResponse createPatient(@Valid @RequestBody PatientRequest request) {
        return patientService.createPatient(request);
    }

    // 查询某个 caregiver 的所有患者
    @GetMapping("/caregiver/{caregiverId}")
    public List<PatientResponse> getPatientsByCaregiver(@PathVariable Long caregiverId) {
        return patientService.getPatientsByCaregiver(caregiverId);
    }

    // 查询单个患者详情
    @GetMapping("/{patientId}")
    public PatientResponse getPatientById(@PathVariable Long patientId) {
        return patientService.getPatientById(patientId);
    }

    // 更新患者
    @PutMapping("/{patientId}")
    public PatientResponse updatePatient(@PathVariable Long patientId,
                                         @Valid @RequestBody PatientRequest request) {
        return patientService.updatePatient(patientId, request);
    }

    // 删除患者
    @DeleteMapping("/{patientId}")
    public String deletePatient(@PathVariable Long patientId) {
        patientService.deletePatient(patientId);
        return "Patient deleted successfully";
    }
}