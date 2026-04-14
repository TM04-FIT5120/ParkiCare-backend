package com.caregiver.controller;

import com.caregiver.dto.PushSubscriptionRequest;
import com.caregiver.service.PushNotificationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push")
@CrossOrigin(origins = "*")
public class PushSubscriptionController {

    private final PushNotificationService pushNotificationService;

    public PushSubscriptionController(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping("/subscribe")
    public String subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        pushNotificationService.saveToken(request);
        return "Push subscription registered successfully";
    }

    @DeleteMapping("/unsubscribe")
    public String unsubscribe(@RequestParam String fcmToken) {
        pushNotificationService.removeToken(fcmToken);
        return "Push subscription removed successfully";
    }
}
