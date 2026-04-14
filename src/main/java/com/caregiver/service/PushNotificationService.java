package com.caregiver.service;

import com.caregiver.dto.PushSubscriptionRequest;
import com.caregiver.entity.PushSubscription;
import com.caregiver.repository.PushSubscriptionRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final FirebaseMessaging firebaseMessaging;

    public PushNotificationService(PushSubscriptionRepository pushSubscriptionRepository,
                                   FirebaseMessaging firebaseMessaging) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.firebaseMessaging = firebaseMessaging;
    }

    /**
     * Upsert an FCM token for a caregiver.
     * If the token already exists, reactivate it and update the timestamp.
     * Otherwise, insert a new record.
     */
    @Transactional
    public void saveToken(PushSubscriptionRequest request) {
        pushSubscriptionRepository.findByFcmToken(request.getFcmToken())
                .ifPresentOrElse(existing -> {
                    existing.setIsActive(true);
                    existing.setCaregiverId(request.getCaregiverId());
                    existing.setDeviceType(request.getDeviceType());
                    existing.setUpdatedAt(LocalDateTime.now());
                    pushSubscriptionRepository.save(existing);
                }, () -> {
                    PushSubscription sub = new PushSubscription();
                    sub.setCaregiverId(request.getCaregiverId());
                    sub.setFcmToken(request.getFcmToken());
                    sub.setDeviceType(request.getDeviceType());
                    sub.setIsActive(true);
                    sub.setCreatedAt(LocalDateTime.now());
                    sub.setUpdatedAt(LocalDateTime.now());
                    pushSubscriptionRepository.save(sub);
                });
    }

    /**
     * Deactivate a specific FCM token (used on logout or explicit unsubscribe).
     */
    @Transactional
    public void removeToken(String fcmToken) {
        pushSubscriptionRepository.findByFcmToken(fcmToken).ifPresent(sub -> {
            sub.setIsActive(false);
            sub.setUpdatedAt(LocalDateTime.now());
            pushSubscriptionRepository.save(sub);
        });
    }

    /**
     * Send a push notification to all active devices registered for a caregiver.
     * Tokens that are no longer registered with FCM are automatically deactivated.
     */
    public void sendToCaregiver(Long caregiverId, String title, String body) {
        List<PushSubscription> tokens = pushSubscriptionRepository
                .findByCaregiverIdAndIsActive(caregiverId, true);

        log.info("[FCM] Sending to caregiverId={} — {} active token(s)", caregiverId, tokens.size());

        for (PushSubscription sub : tokens) {
            Message message = Message.builder()
                    .setToken(sub.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();
            try {
                String messageId = firebaseMessaging.send(message);
                log.info("[FCM] Sent OK — tokenId={} messageId={}", sub.getId(), messageId);
            } catch (FirebaseMessagingException e) {
                if (MessagingErrorCode.UNREGISTERED.equals(e.getMessagingErrorCode())
                        || MessagingErrorCode.INVALID_ARGUMENT.equals(e.getMessagingErrorCode())) {
                    log.warn("[FCM] Stale token deactivated — tokenId={} error={}", sub.getId(), e.getMessagingErrorCode());
                    sub.setIsActive(false);
                    sub.setUpdatedAt(LocalDateTime.now());
                    pushSubscriptionRepository.save(sub);
                } else {
                    // Log full cause chain to identify the real underlying error
                    Throwable root = e;
                    while (root.getCause() != null) root = root.getCause();
                    log.error("[FCM] Send failed — tokenId={} error={} message={} rootCause={}",
                            sub.getId(), e.getMessagingErrorCode(), e.getMessage(), root.toString(), e);
                }
            }
        }
    }
}
