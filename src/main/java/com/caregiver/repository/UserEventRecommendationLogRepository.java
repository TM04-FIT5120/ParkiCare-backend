package com.caregiver.repository;

import com.caregiver.entity.UserEventRecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserEventRecommendationLogRepository extends JpaRepository<UserEventRecommendationLog, Long> {

    List<UserEventRecommendationLog> findByCaregiverIdAndUserFeedbackAndEventDateGreaterThanEqualOrderByIdDesc(
            Long caregiverId, String userFeedback, LocalDate fromDate);
}
