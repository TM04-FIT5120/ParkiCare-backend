package com.caregiver.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Mirrors frontend {@code calculateDoseTimes} in CareEventsPage.tsx.
 * Meal schedules are start times; each meal blocks {@link #MEAL_DURATION_MINUTES} minutes.
 * Before/with meals anchor to start; after meals anchors to meal end then applies offset.
 */
public final class MealDoseTimeCalculator {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MINUTES_PER_DAY = 1440;
    /** Meal events block this many minutes from scheduled start (see ScheduleCascadeService). */
    public static final int MEAL_DURATION_MINUTES = 60;

    private static final Map<String, Integer> MEAL_OFFSET_MINUTES = Map.of(
            "before meals", -60,
            "after meals", 60,
            "with meals", 0
    );

    private static final Map<String, LocalTime> DEFAULT_MEAL_TIMES = Map.of(
            "BREAKFAST", LocalTime.of(8, 0),
            "LUNCH", LocalTime.of(13, 0),
            "DINNER", LocalTime.of(19, 0)
    );

    private MealDoseTimeCalculator() {
    }

    public static List<LocalTime> calculateDoseTimes(
            List<String> anchoredMeals,
            Map<String, String> mealTimesByType,
            String mealTiming,
            int intervalMinutes) {

        if (anchoredMeals == null || anchoredMeals.isEmpty()) {
            return List.of();
        }

        String normalizedTiming = mealTiming == null ? "" : mealTiming.trim().toLowerCase(Locale.ROOT);
        int offset = MEAL_OFFSET_MINUTES.getOrDefault(
                normalizedTiming,
                MEAL_OFFSET_MINUTES.get("after meals"));

        List<MealMinutePair> pairs = new ArrayList<>();
        for (String meal : anchoredMeals) {
            String upper = meal.toUpperCase(Locale.ROOT);
            String hhmm = mealTimesByType.getOrDefault(upper,
                    DEFAULT_MEAL_TIMES.get(upper).format(TIME_FMT));
            int mealStartMinutes = timeToMinute(hhmm);
            int anchorMinutes = mealStartMinutes;
            if ("after meals".equals(normalizedTiming)) {
                anchorMinutes = mealStartMinutes + MEAL_DURATION_MINUTES;
            }
            int totalMinutes = ((anchorMinutes + offset) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY;
            pairs.add(new MealMinutePair(upper, totalMinutes));
        }

        pairs.sort(Comparator.comparingInt(p -> p.totalMinutes));

        for (int i = 1; i < pairs.size(); i++) {
            int minAllowed = pairs.get(i - 1).totalMinutes + intervalMinutes;
            if (pairs.get(i).totalMinutes < minAllowed % MINUTES_PER_DAY) {
                pairs.set(i, new MealMinutePair(pairs.get(i).mealType, minAllowed % MINUTES_PER_DAY));
            }
        }

        List<LocalTime> result = new ArrayList<>(pairs.size());
        for (MealMinutePair p : pairs) {
            result.add(minuteToTime(p.totalMinutes));
        }
        return result;
    }

    public static List<String> parseAnchoredMeals(String anchoredMealsCsv) {
        if (anchoredMealsCsv == null || anchoredMealsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(anchoredMealsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .toList();
    }

    private static int timeToMinute(String hhmm) {
        LocalTime t = LocalTime.parse(hhmm.trim(), TIME_FMT);
        return t.getHour() * 60 + t.getMinute();
    }

    private static LocalTime minuteToTime(int minute) {
        int norm = ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        return LocalTime.of(norm / 60, norm % 60);
    }

    private record MealMinutePair(String mealType, int totalMinutes) {
    }
}
