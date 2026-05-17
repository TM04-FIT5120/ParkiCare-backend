package com.caregiver.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MealDoseTimeCalculatorTest {

    @Test
    void calculateDoseTimes_afterMealsWithInterval() {
        Map<String, String> meals = Map.of(
                "BREAKFAST", "08:00",
                "LUNCH", "13:00",
                "DINNER", "19:00"
        );
        List<LocalTime> times = MealDoseTimeCalculator.calculateDoseTimes(
                List.of("BREAKFAST", "LUNCH"),
                meals,
                "after meals",
                60);

        assertEquals(2, times.size());
        assertEquals(LocalTime.of(10, 0), times.get(0));
        assertEquals(LocalTime.of(15, 0), times.get(1));
    }

    @Test
    void calculateDoseTimes_afterMeals_oneHourAfterMealEnd() {
        Map<String, String> meals = Map.of("BREAKFAST", "08:30");
        List<LocalTime> times = MealDoseTimeCalculator.calculateDoseTimes(
                List.of("BREAKFAST"),
                meals,
                "after meals",
                0);

        assertEquals(1, times.size());
        assertEquals(LocalTime.of(10, 30), times.get(0));
    }

    @Test
    void calculateDoseTimes_enforcesMinimumInterval() {
        Map<String, String> meals = new LinkedHashMap<>();
        meals.put("BREAKFAST", "08:00");
        meals.put("LUNCH", "08:30");

        List<LocalTime> times = MealDoseTimeCalculator.calculateDoseTimes(
                List.of("BREAKFAST", "LUNCH"),
                meals,
                "with meals",
                90);

        assertEquals(LocalTime.of(8, 0), times.get(0));
        assertEquals(LocalTime.of(9, 30), times.get(1));
    }

    @Test
    void parseAnchoredMeals_trimsAndUppercases() {
        assertEquals(List.of("BREAKFAST", "DINNER"),
                MealDoseTimeCalculator.parseAnchoredMeals(" breakfast, dinner "));
    }
}
