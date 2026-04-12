package com.caregiver.service;

import com.caregiver.dto.HomeCareScheduleRequest;
import com.caregiver.dto.HomeCareScheduleResponse;
import com.caregiver.entity.Patient;
import com.caregiver.entity.PatientHomeCareSchedule;
import com.caregiver.repository.PatientHomeCareScheduleRepository;
import com.caregiver.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PatientHomeCareScheduleService {

    private final PatientHomeCareScheduleRepository repository;
    private final PatientRepository patientRepository;

    public PatientHomeCareScheduleService(PatientHomeCareScheduleRepository repository,
                                          PatientRepository patientRepository) {
        this.repository = repository;
        this.patientRepository = patientRepository;
    }

    public HomeCareScheduleResponse create(HomeCareScheduleRequest request) {
        verifyPatientOwnership(request.getPatientId(), request.getCaregiverId());

        PatientHomeCareSchedule entity = new PatientHomeCareSchedule();
        entity.setPatientId(request.getPatientId());
        entity.setHomeCareTitle(request.getHomeCareTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setCareNote(request.getCareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        entity.setIsUrgent(request.getIsUrgent() == null ? 0 : request.getIsUrgent());
        entity.setRecurrence(request.getRecurrence() == null ? "none" : request.getRecurrence());

        return toResponse(repository.save(entity));
    }

    public List<HomeCareScheduleResponse> getByPatient(Long patientId) {
        return repository.findByPatientId(patientId).stream().map(this::toResponse).toList();
    }

    public HomeCareScheduleResponse update(Long id, HomeCareScheduleRequest request) {
        PatientHomeCareSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Home care event not found"));

        verifyPatientOwnership(entity.getPatientId(), request.getCaregiverId());

        entity.setPatientId(request.getPatientId());
        entity.setHomeCareTitle(request.getHomeCareTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setCareNote(request.getCareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        entity.setIsUrgent(request.getIsUrgent() == null ? 0 : request.getIsUrgent());
        entity.setRecurrence(request.getRecurrence() == null ? "none" : request.getRecurrence());

        return toResponse(repository.save(entity));
    }

    public void delete(Long id, Long caregiverId) {
        PatientHomeCareSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Home care event not found"));
        verifyPatientOwnership(entity.getPatientId(), caregiverId);
        repository.delete(entity);
    }

    private void verifyPatientOwnership(Long patientId, Long caregiverId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!patient.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
    }

    private HomeCareScheduleResponse toResponse(PatientHomeCareSchedule entity) {
        return new HomeCareScheduleResponse(
                entity.getId(),
                entity.getPatientId(),
                entity.getHomeCareTitle(),
                entity.getStartDatetime() != null ? entity.getStartDatetime().toString() : null,
                entity.getEndDatetime() != null ? entity.getEndDatetime().toString() : null,
                entity.getCareNote(),
                entity.getIsCompleted(),
                entity.getIsUrgent(),
                entity.getRecurrence()
        );
    }
}
