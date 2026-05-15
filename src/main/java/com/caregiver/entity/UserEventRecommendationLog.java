package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "user_event_recommendation_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
// Feedback-learning log for AI event recommendations.
public class UserEventRecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "aqi")
    private Integer aqi;

    @Column(name = "weather", length = 50)
    private String weather;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Column(name = "medication_period", nullable = false, length = 30)
    private String medicationPeriod;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    @Column(name = "user_feedback", nullable = false, length = 20)
    private String userFeedback;

    @Column(name = "score", nullable = false)
    private Integer score;
}
