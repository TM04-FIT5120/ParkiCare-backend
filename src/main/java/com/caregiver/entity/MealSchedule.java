package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "meal_schedule")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "meal_type", nullable = false, length = 20)
    private String mealType; // BREAKFAST | LUNCH | DINNER

    @JdbcTypeCode(SqlTypes.TIME)
    @Column(name = "meal_time", nullable = false)
    private LocalTime mealTime;

    /**
     * When this row was logged (MYT wall clock from the app). Same MYT calendar day + caregiver +
     * meal_type is treated as one logical row (upsert updates this row).
     * The recorded_at timestamp.
     */
    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;
}
