package com.caregiver.service;

import com.caregiver.dto.OutdoorScheduleRequest;
import com.caregiver.dto.OutdoorScheduleResponse;
import com.caregiver.entity.Patient;
import com.caregiver.entity.PatientOutdoorSchedule;
import com.caregiver.repository.PatientOutdoorScheduleRepository;
import com.caregiver.repository.PatientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PatientOutdoorScheduleService {

    private final PatientOutdoorScheduleRepository repository;
    private final PatientRepository patientRepository;

    public PatientOutdoorScheduleService(PatientOutdoorScheduleRepository repository,
                                         PatientRepository patientRepository) {
        this.repository = repository;
        this.patientRepository = patientRepository;
    }

    public OutdoorScheduleResponse create(OutdoorScheduleRequest request) {
        verifyPatientOwnership(request.getPatientId(), request.getCaregiverId());

        PatientOutdoorSchedule entity = new PatientOutdoorSchedule();
        entity.setPatientId(request.getPatientId());
        entity.setOutdoorTitle(request.getOutdoorTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setPrepareNote(request.getPrepareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        entity.setRecurrence(request.getRecurrence() == null ? "none" : request.getRecurrence());

        return toResponse(repository.save(entity));
    }

    public List<OutdoorScheduleResponse> getByPatient(Long patientId) {
        return repository.findByPatientIdAndIsDeleted(patientId, 0).stream().map(this::toResponse).toList();
    }

    public OutdoorScheduleResponse update(Long id, OutdoorScheduleRequest request) {
        PatientOutdoorSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outdoor event not found"));

        verifyPatientOwnership(entity.getPatientId(), request.getCaregiverId());

        entity.setPatientId(request.getPatientId());
        entity.setOutdoorTitle(request.getOutdoorTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setPrepareNote(request.getPrepareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        entity.setRecurrence(request.getRecurrence() == null ? "none" : request.getRecurrence());

        return toResponse(repository.save(entity));
    }

    public void delete(Long id, Long caregiverId) {
        PatientOutdoorSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outdoor event not found"));
        verifyPatientOwnership(entity.getPatientId(), caregiverId);
        entity.setIsDeleted(1);
        repository.save(entity);
    }

    public OutdoorScheduleResponse togglePin(Long id, Long caregiverId) {
        PatientOutdoorSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outdoor event not found"));
        verifyPatientOwnership(entity.getPatientId(), caregiverId);
        entity.setIsPinned(entity.getIsPinned() != null && entity.getIsPinned() == 1 ? 0 : 1);
        return toResponse(repository.save(entity));
    }

    private void verifyPatientOwnership(Long patientId, Long caregiverId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!patient.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
    }

    private OutdoorScheduleResponse toResponse(PatientOutdoorSchedule entity) {
        return new OutdoorScheduleResponse(
                entity.getId(),
                entity.getPatientId(),
                entity.getOutdoorTitle(),
                entity.getStartDatetime() != null ? entity.getStartDatetime().toString() : null,
                entity.getEndDatetime() != null ? entity.getEndDatetime().toString() : null,
                entity.getPrepareNote(),
                entity.getIsCompleted(),
                entity.getRecurrence(),
                entity.getIsPinned()
        );
    }
}
