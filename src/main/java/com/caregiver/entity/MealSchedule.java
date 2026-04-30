package com.caregiver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "meal_schedule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"caregiver_id", "meal_type"}))
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

    @Column(name = "meal_time", nullable = false)
    private LocalTime mealTime;
}
