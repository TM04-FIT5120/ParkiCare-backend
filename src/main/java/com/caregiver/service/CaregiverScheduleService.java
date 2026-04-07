package com.caregiver.service;

import com.caregiver.dto.CaregiverScheduleRequest;
import com.caregiver.dto.CaregiverScheduleResponse;
import com.caregiver.entity.CaregiverSchedule;
import com.caregiver.repository.CaregiverRepository;
import com.caregiver.repository.CaregiverScheduleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CaregiverScheduleService {

    private final CaregiverScheduleRepository caregiverScheduleRepository;
    private final CaregiverRepository caregiverRepository;

    public CaregiverScheduleService(CaregiverScheduleRepository caregiverScheduleRepository,
                                    CaregiverRepository caregiverRepository) {
        this.caregiverScheduleRepository = caregiverScheduleRepository;
        this.caregiverRepository = caregiverRepository;
    }

    public CaregiverScheduleResponse createSchedule(CaregiverScheduleRequest request) {
        if (!caregiverRepository.existsById(request.getCaregiverId())) {
            throw new RuntimeException("Caregiver not found");
        }

        CaregiverSchedule schedule = new CaregiverSchedule();
        schedule.setCaregiverId(request.getCaregiverId());
        schedule.setScheduleTitle(request.getScheduleTitle().trim());
        schedule.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        schedule.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        schedule.setScheduleNote(request.getScheduleNote());
        schedule.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        schedule.setIsConflict(request.getIsConflict() == null ? 0 : request.getIsConflict());

        CaregiverSchedule saved = caregiverScheduleRepository.save(schedule);
        return toResponse(saved);
    }

    public List<CaregiverScheduleResponse> getSchedulesByCaregiver(Long caregiverId) {
        return caregiverScheduleRepository.findByCaregiverId(caregiverId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CaregiverScheduleResponse getScheduleById(Long id) {
        CaregiverSchedule schedule = caregiverScheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Caregiver schedule not found"));
        return toResponse(schedule);
    }

    public CaregiverScheduleResponse updateSchedule(Long id, CaregiverScheduleRequest request) {
        CaregiverSchedule schedule = caregiverScheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Caregiver schedule not found"));

        if (!caregiverRepository.existsById(request.getCaregiverId())) {
            throw new RuntimeException("Caregiver not found");
        }

        schedule.setCaregiverId(request.getCaregiverId());
        schedule.setScheduleTitle(request.getScheduleTitle().trim());
        schedule.setStartDatetime(LocalDateTime.parse(request.getStartDatetime()));
        schedule.setEndDatetime(LocalDateTime.parse(request.getEndDatetime()));
        schedule.setScheduleNote(request.getScheduleNote());
        schedule.setIsCompleted(request.getIsCompleted() == null ? 0 : request.getIsCompleted());
        schedule.setIsConflict(request.getIsConflict() == null ? 0 : request.getIsConflict());

        CaregiverSchedule updated = caregiverScheduleRepository.save(schedule);
        return toResponse(updated);
    }

    public void deleteSchedule(Long id) {
        CaregiverSchedule schedule = caregiverScheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Caregiver schedule not found"));

        caregiverScheduleRepository.delete(schedule);
    }

    private CaregiverScheduleResponse toResponse(CaregiverSchedule schedule) {
        return new CaregiverScheduleResponse(
                schedule.getId(),
                schedule.getCaregiverId(),
                schedule.getScheduleTitle(),
                schedule.getStartDatetime() != null ? schedule.getStartDatetime().toString() : null,
                schedule.getEndDatetime() != null ? schedule.getEndDatetime().toString() : null,
                schedule.getScheduleNote(),
                schedule.getIsCompleted(),
                schedule.getIsConflict()
        );
    }
}