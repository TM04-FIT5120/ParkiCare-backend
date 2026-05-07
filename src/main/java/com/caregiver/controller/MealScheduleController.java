package com.caregiver.controller;

import com.caregiver.dto.MealScheduleResponse;
import com.caregiver.service.MealScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/meal-schedule")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MealScheduleController {

    private final MealScheduleService mealScheduleService;

    @GetMapping("/caregiver/{caregiverId}")
    public List<MealScheduleResponse> getMealSchedules(@PathVariable Long caregiverId) {
        return mealScheduleService.getMealSchedules(caregiverId);
    }

    @PostMapping("/caregiver/{caregiverId}/refresh-predictions")
    public List<MealScheduleResponse> refreshPredictedMealTimes(@PathVariable Long caregiverId) {
        return mealScheduleService.refreshPredictedMealTimes(caregiverId);
    }

    @PutMapping("/caregiver/{caregiverId}/meal/{mealType}")
    public MealScheduleResponse updateMealTime(
            @PathVariable Long caregiverId,
            @PathVariable String mealType,
            @RequestBody Map<String, String> body) {
        return mealScheduleService.updateMealTime(caregiverId, mealType, body.get("mealTime"));
    }

    @PostMapping("/caregiver/{caregiverId}/generate-week")
    public void generateWeeklyMeals(@PathVariable Long caregiverId) {
        mealScheduleService.generateWeeklyMeals(caregiverId);
    }
}
