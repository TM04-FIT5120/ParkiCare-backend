package com.caregiver.service;

import com.caregiver.dto.HomeCareScheduleRequest;
import com.caregiver.dto.HomeCareScheduleResponse;
import com.caregiver.entity.PatientHomeCareSchedule;
import com.caregiver.repository.PatientHomeCareScheduleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PatientHomeCareScheduleService {

    private final PatientHomeCareScheduleRepository repository;

    public PatientHomeCareScheduleService(PatientHomeCareScheduleRepository repository) {
        this.repository = repository;
    }

    public HomeCareScheduleResponse create(HomeCareScheduleRequest request) {
        PatientHomeCareSchedule entity = new PatientHomeCareSchedule();
        entity.setPatientId(request.getPatientId());
        entity.setHomeCareTitle(request.getHomeCareTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setCareNote(request.getCareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        entity.setIsUrgent(request.getIsUrgent() == null ? 0 : request.getIsUrgent());

        return toResponse(repository.save(entity));
    }

    public List<HomeCareScheduleResponse> getByPatient(Long patientId) {
        return repository.findByPatientId(patientId).stream().map(this::toResponse).toList();
    }

    public HomeCareScheduleResponse update(Long id, HomeCareScheduleRequest request) {
        PatientHomeCareSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Home care event not found"));

        entity.setPatientId(request.getPatientId());
        entity.setHomeCareTitle(request.getHomeCareTitle());
        entity.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        entity.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        entity.setCareNote(request.getCareNote());
        entity.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        entity.setIsUrgent(request.getIsUrgent() == null ? 0 : request.getIsUrgent());

        return toResponse(repository.save(entity));
    }

    public void delete(Long id) {
        PatientHomeCareSchedule entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Home care event not found"));
        repository.delete(entity);
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
                entity.getIsUrgent()
        );
    }
}