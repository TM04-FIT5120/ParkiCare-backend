package com.caregiver.service;

import com.caregiver.dto.CaregiverAlertResponse;
import com.caregiver.entity.CaregiverInAppAlert;
import com.caregiver.repository.CaregiverInAppAlertRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CaregiverAlertService {

    public static final String ALERT_TYPE_SCHEDULE_OVERLAP = "SCHEDULE_OVERLAP";

    private final CaregiverInAppAlertRepository alertRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Transactional
    public void createOverlapAlert(
            Long caregiverId,
            String conflictKind,
            Map<String, Object> rescheduledEvent,
            Map<String, Object> conflictingEvent) {
        String dedupeKey = buildDedupeKey(caregiverId, rescheduledEvent, conflictingEvent);
        if (RECENT_DEDUPE.contains(dedupeKey)) {
            return;
        }
        RECENT_DEDUPE.add(dedupeKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conflictKind", conflictKind);
        payload.put("rescheduledEvent", rescheduledEvent);
        payload.put("conflictingEvent", conflictingEvent);

        CaregiverInAppAlert alert = new CaregiverInAppAlert();
        alert.setCaregiverId(caregiverId);
        alert.setAlertType(ALERT_TYPE_SCHEDULE_OVERLAP);
        try {
            alert.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            alert.setPayloadJson("{\"conflictKind\":\"" + conflictKind + "\"}");
        }
        alert.setCreatedAt(LocalDateTime.now());
        alertRepository.save(alert);
    }

    private static final Set<String> RECENT_DEDUPE = ConcurrentHashMap.newKeySet();

    private String buildDedupeKey(Long caregiverId, Map<String, Object> a, Map<String, Object> b) {
        return caregiverId + "|" + a.get("id") + "|" + a.get("source") + "|" + b.get("id") + "|" + b.get("source");
    }

    public List<CaregiverAlertResponse> getUnreadAlerts(Long caregiverId) {
        return alertRepository.findByCaregiverIdAndReadAtIsNullOrderByCreatedAtDesc(caregiverId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void dismissAlert(Long caregiverId, Long alertId) {
        CaregiverInAppAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        if (!caregiverId.equals(alert.getCaregiverId())) {
            throw new RuntimeException("Alert does not belong to caregiver");
        }
        alert.setReadAt(LocalDateTime.now());
        alertRepository.save(alert);
    }

    @Transactional
    public void dismissAllAlerts(Long caregiverId) {
        for (CaregiverInAppAlert alert : alertRepository.findByCaregiverIdAndReadAtIsNullOrderByCreatedAtDesc(caregiverId)) {
            alert.setReadAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
    }

    private CaregiverAlertResponse toResponse(CaregiverInAppAlert alert) {
        return new CaregiverAlertResponse(
                alert.getId(),
                alert.getAlertType(),
                alert.getPayloadJson(),
                alert.getCreatedAt() == null ? null : alert.getCreatedAt().format(ISO));
    }
}
