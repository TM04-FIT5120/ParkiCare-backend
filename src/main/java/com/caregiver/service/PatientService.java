package com.caregiver.service;

import com.caregiver.dto.PatientRequest;
import com.caregiver.dto.PatientResponse;
import com.caregiver.entity.Patient;
import com.caregiver.repository.CaregiverRepository;
import com.caregiver.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final CaregiverRepository caregiverRepository;

    public PatientService(PatientRepository patientRepository,
                          CaregiverRepository caregiverRepository) {
        this.patientRepository = patientRepository;
        this.caregiverRepository = caregiverRepository;
    }

    public PatientResponse createPatient(PatientRequest request) {
        if (!caregiverRepository.existsById(request.getCaregiverId())) {
            throw new RuntimeException("Caregiver not found");
        }

        Patient patient = new Patient();
        patient.setCaregiverId(request.getCaregiverId());
        patient.setPatientNickname(request.getPatientNickname().trim());
        patient.setAgeRange(request.getAgeRange());
        patient.setRemark(request.getRemark());

        Patient saved = patientRepository.save(patient);
        return convertToResponse(saved);
    }

    public List<PatientResponse> getPatientsByCaregiver(Long caregiverId) {
        List<Patient> patients = patientRepository.findByCaregiverId(caregiverId);
        return patients.stream()
                .map(this::convertToResponse)
                .toList();
    }

    public PatientResponse getPatientById(Long patientId, Long caregiverId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!patient.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
        return convertToResponse(patient);
    }

    public PatientResponse updatePatient(Long patientId, PatientRequest request) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        if (!caregiverRepository.existsById(request.getCaregiverId())) {
            throw new RuntimeException("Caregiver not found");
        }
        if (!patient.getCaregiverId().equals(request.getCaregiverId())) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }

        patient.setPatientNickname(request.getPatientNickname().trim());
        patient.setAgeRange(request.getAgeRange());
        patient.setRemark(request.getRemark());

        Patient updated = patientRepository.save(patient);
        return convertToResponse(updated);
    }

    public void deletePatient(Long patientId, Long caregiverId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!patient.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
        patientRepository.delete(patient);
    }

    private PatientResponse convertToResponse(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getCaregiverId(),
                patient.getPatientNickname(),
                patient.getAgeRange(),
                patient.getRemark()
        );
    }
}