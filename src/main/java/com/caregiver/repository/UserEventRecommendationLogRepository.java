package com.caregiver.repository;

import com.caregiver.entity.UserEventRecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface UserEventRecommendationLogRepository extends JpaRepository<UserEventRecommendationLog, Long> {

    List<UserEventRecommendationLog> findByCaregiverIdAndUserFeedbackAndEventDateGreaterThanEqualOrderByIdDesc(
            Long caregiverId, String userFeedback, LocalDate fromDate);

    /** 最近窗口内 score 最高的 5 条接受反馈（同分按 id 新在前） */
    @Query(value = """
            SELECT * FROM user_event_recommendation_log
            WHERE caregiver_id = :caregiverId AND user_feedback = :userFeedback AND event_date >= :fromDate
            ORDER BY score DESC, id DESC
            LIMIT 5
            """, nativeQuery = true)
    List<UserEventRecommendationLog> findTop5AcceptByScoreDesc(
            @Param("caregiverId") Long caregiverId,
            @Param("userFeedback") String userFeedback,
            @Param("fromDate") LocalDate fromDate);

    /** 最近窗口内 score 最低的 5 条拒绝反馈（同分按 id 新在前） */
    @Query(value = """
            SELECT * FROM user_event_recommendation_log
            WHERE caregiver_id = :caregiverId AND user_feedback = :userFeedback AND event_date >= :fromDate
            ORDER BY score ASC, id DESC
            LIMIT 5
            """, nativeQuery = true)
    List<UserEventRecommendationLog> findTop5RejectByScoreAsc(
            @Param("caregiverId") Long caregiverId,
            @Param("userFeedback") String userFeedback,
            @Param("fromDate") LocalDate fromDate);
}
