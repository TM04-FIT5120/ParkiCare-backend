package com.caregiver.controller;

import com.caregiver.dto.CaregiverAlertResponse;
import com.caregiver.service.CaregiverAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/caregiver")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CaregiverAlertController {

    private final CaregiverAlertService caregiverAlertService;

    @GetMapping("/{caregiverId}/alerts/unread")
    public List<CaregiverAlertResponse> getUnreadAlerts(@PathVariable Long caregiverId) {
        return caregiverAlertService.getUnreadAlerts(caregiverId);
    }

    @PostMapping("/{caregiverId}/alerts/{alertId}/dismiss")
    public Map<String, String> dismissAlert(
            @PathVariable Long caregiverId,
            @PathVariable Long alertId) {
        caregiverAlertService.dismissAlert(caregiverId, alertId);
        return Map.of("status", "ok");
    }

    @PostMapping("/{caregiverId}/alerts/dismiss-all")
    public Map<String, String> dismissAll(@PathVariable Long caregiverId) {
        caregiverAlertService.dismissAllAlerts(caregiverId);
        return Map.of("status", "ok");
    }
}
