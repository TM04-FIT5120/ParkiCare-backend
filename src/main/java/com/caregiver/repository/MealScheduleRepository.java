package com.caregiver.repository;

import com.caregiver.entity.MealSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MealScheduleRepository extends JpaRepository<MealSchedule, Long> {

    List<MealSchedule> findByCaregiverId(Long caregiverId);

    Optional<MealSchedule> findByCaregiverIdAndMealType(Long caregiverId, String mealType);
}
