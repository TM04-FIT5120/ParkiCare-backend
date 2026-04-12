package com.caregiver.service;

import com.caregiver.dto.CaregiverEventOccurrenceResponse;
import com.caregiver.dto.UpsertCaregiverEventOccurrenceRequest;
import com.caregiver.entity.CaregiverEventOccurrence;
import com.caregiver.entity.CaregiverSchedule;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.entity.Patient;
import com.caregiver.entity.PatientHomeCareSchedule;
import com.caregiver.entity.PatientOutdoorSchedule;
import com.caregiver.model.EventOccurrenceSourceType;
import com.caregiver.repository.CaregiverEventOccurrenceRepository;
import com.caregiver.repository.CaregiverScheduleRepository;
import com.caregiver.repository.MedicationReminderRepository;
import com.caregiver.repository.PatientHomeCareScheduleRepository;
import com.caregiver.repository.PatientOutdoorScheduleRepository;
import com.caregiver.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CaregiverEventOccurrenceService {

    private final CaregiverEventOccurrenceRepository occurrenceRepository;
    private final CaregiverScheduleRepository caregiverScheduleRepository;
    private final PatientHomeCareScheduleRepository homeCareScheduleRepository;
    private final PatientOutdoorScheduleRepository outdoorScheduleRepository;
    private final MedicationReminderRepository medicationReminderRepository;
    private final PatientRepository patientRepository;

    public CaregiverEventOccurrenceService(
            CaregiverEventOccurrenceRepository occurrenceRepository,
            CaregiverScheduleRepository caregiverScheduleRepository,
            PatientHomeCareScheduleRepository homeCareScheduleRepository,
            PatientOutdoorScheduleRepository outdoorScheduleRepository,
            MedicationReminderRepository medicationReminderRepository,
            PatientRepository patientRepository) {
        this.occurrenceRepository = occurrenceRepository;
        this.caregiverScheduleRepository = caregiverScheduleRepository;
        this.homeCareScheduleRepository = homeCareScheduleRepository;
        this.outdoorScheduleRepository = outdoorScheduleRepository;
        this.medicationReminderRepository = medicationReminderRepository;
        this.patientRepository = patientRepository;
    }

    public List<CaregiverEventOccurrenceResponse> listBetween(Long caregiverId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new RuntimeException("from and to dates are required");
        }
        if (to.isBefore(from)) {
            throw new RuntimeException("to date must be on or after from date");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        return occurrenceRepository
                .findByCaregiverIdAndOccurrenceStartGreaterThanEqualAndOccurrenceStartBefore(
                        caregiverId, start, endExclusive)
                .stream()
                .filter(o -> o.getCompleted() != null && o.getCompleted() == 1)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CaregiverEventOccurrenceResponse upsert(UpsertCaregiverEventOccurrenceRequest request) {
        EventOccurrenceSourceType sourceType = parseSourceType(request.getSourceType());
        LocalDateTime occurrenceStart = LocalDateTime.parse(request.getOccurrenceStart());
        Long caregiverId = request.getCaregiverId();
        Long sourceId = request.getSourceId();

        assertCaregiverOwnsSource(caregiverId, sourceType, sourceId);

        if (Boolean.TRUE.equals(request.getCompleted())) {
            CaregiverEventOccurrence row = occurrenceRepository
                    .findByCaregiverIdAndSourceTypeAndSourceIdAndOccurrenceStart(
                            caregiverId, sourceType, sourceId, occurrenceStart)
                    .orElseGet(CaregiverEventOccurrence::new);
            row.setCaregiverId(caregiverId);
            row.setSourceType(sourceType);
            row.setSourceId(sourceId);
            row.setOccurrenceStart(occurrenceStart);
            row.setCompleted(1);
            CaregiverEventOccurrence saved = occurrenceRepository.save(row);
            return toResponse(saved);
        }

        occurrenceRepository.deleteByCaregiverIdAndSourceTypeAndSourceIdAndOccurrenceStart(
                caregiverId, sourceType, sourceId, occurrenceStart);
        return new CaregiverEventOccurrenceResponse(
                null, caregiverId, sourceType.name(), sourceId, request.getOccurrenceStart(), 0);
    }

    private EventOccurrenceSourceType parseSourceType(String raw) {
        try {
            return EventOccurrenceSourceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid sourceType: " + raw);
        }
    }

    private void assertCaregiverOwnsSource(Long caregiverId, EventOccurrenceSourceType type, Long sourceId) {
        switch (type) {
            case CAREGIVER_SCHEDULE -> {
                CaregiverSchedule s = caregiverScheduleRepository.findById(sourceId)
                        .orElseThrow(() -> new RuntimeException("Caregiver schedule not found"));
                if (!s.getCaregiverId().equals(caregiverId)) {
                    throw new RuntimeException("Access denied: schedule does not belong to this caregiver");
                }
            }
            case PATIENT_HOME_CARE -> {
                PatientHomeCareSchedule h = homeCareScheduleRepository.findById(sourceId)
                        .orElseThrow(() -> new RuntimeException("Home care schedule not found"));
                assertPatientBelongsToCaregiver(h.getPatientId(), caregiverId);
            }
            case PATIENT_OUTDOOR -> {
                PatientOutdoorSchedule o = outdoorScheduleRepository.findById(sourceId)
                        .orElseThrow(() -> new RuntimeException("Outdoor schedule not found"));
                assertPatientBelongsToCaregiver(o.getPatientId(), caregiverId);
            }
            case MEDICATION_PLAN -> {
                MedicationPlan m = medicationReminderRepository.findById(sourceId)
                        .orElseThrow(() -> new RuntimeException("Medication plan not found"));
                assertPatientBelongsToCaregiver(m.getPatientId(), caregiverId);
            }
        }
    }

    private void assertPatientBelongsToCaregiver(Long patientId, Long caregiverId) {
        Patient p = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));
        if (!p.getCaregiverId().equals(caregiverId)) {
            throw new RuntimeException("Access denied: patient does not belong to this caregiver");
        }
    }

    private CaregiverEventOccurrenceResponse toResponse(CaregiverEventOccurrence o) {
        return new CaregiverEventOccurrenceResponse(
                o.getId(),
                o.getCaregiverId(),
                o.getSourceType().name(),
                o.getSourceId(),
                o.getOccurrenceStart() != null ? o.getOccurrenceStart().toString() : null,
                o.getCompleted()
        );
    }
}
