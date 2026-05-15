package com.caregiver.repository;

import com.caregiver.entity.MealSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealScheduleRepository extends JpaRepository<MealSchedule, Long> {

    List<MealSchedule> findByCaregiverId(Long caregiverId);

    List<MealSchedule> findByCaregiverIdAndMealTypeOrderByIdAsc(
            Long caregiverId, String mealType);
}
