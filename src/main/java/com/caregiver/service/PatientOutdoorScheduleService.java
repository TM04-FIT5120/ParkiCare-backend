package com.caregiver.service;

import com.caregiver.dto.OutdoorScheduleRequest;
import com.caregiver.dto.OutdoorScheduleResponse;
import com.caregiver.entity.PatientOutdoorSchedule;
import com.caregiver.repository.PatientOutdoorScheduleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PatientOutdoorScheduleService {

    private final PatientOutdoorScheduleRepository repository;

    public PatientOutdoorScheduleService(PatientOutdoorScheduleRepository repository) {
        this.repository = repository;
    }

    public OutdoorScheduleResponse create(OutdoorScheduleRequest request) {
        PatientOutdoorSchedule entity = new PatientOutdoorSchedule();
        entity.setPatientId(request.getPatientId());
        entity.setOutdoorTitle(request.getOutdoorTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setPrepareNote(request.getPrepareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());

        return toResponse(repository.save(entity));
    }

    public List<OutdoorScheduleResponse> getByPatient(Long patientId) {
        return repository.findByPatientId(patientId).stream().map(this::toResponse).toList();
    }

    public OutdoorScheduleResponse update(Long id, OutdoorScheduleRequest request) {
        PatientOutdoorSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outdoor event not found"));

        entity.setPatientId(request.getPatientId());
        entity.setOutdoorTitle(request.getOutdoorTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setPrepareNote(request.getPrepareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());

        return toResponse(repository.save(entity));
    }

    public void delete(Long id) {
        PatientOutdoorSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Outdoor event not found"));
        repository.delete(entity);
    }

    private OutdoorScheduleResponse toResponse(PatientOutdoorSchedule entity) {
        return new OutdoorScheduleResponse(
                entity.getId(),
                entity.getPatientId(),
                entity.getOutdoorTitle(),
                entity.getStartDatetime() != null ? entity.getStartDatetime().toString() : null,
                entity.getEndDatetime() != null ? entity.getEndDatetime().toString() : null,
                entity.getPrepareNote(),
                entity.getIsCompleted()
        );
    }
}