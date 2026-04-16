package com.caregiver.repository;

import com.caregiver.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByCaregiverIdAndIsActive(Long caregiverId, Boolean isActive);

    Optional<PushSubscription> findByCaregiverIdAndDeviceId(Long caregiverId, String deviceId);

    Optional<PushSubscription> findByFcmToken(String fcmToken);
}
