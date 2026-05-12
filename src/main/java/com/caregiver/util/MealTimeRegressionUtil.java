package com.caregiver.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MealTimeRegressionUtil {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MINUTES_PER_DAY = 24 * 60;

    private MealTimeRegressionUtil() {
    }

    public static int timeToMinute(String time) {
        LocalTime localTime = LocalTime.parse(time, TIME_FMT);
        return localTime.getHour() * 60 + localTime.getMinute();
    }

    public static String minuteToTime(int minute) {
        int normalizedMinute = Math.max(0, Math.min(minute, MINUTES_PER_DAY - 1));
        return LocalTime.of(normalizedMinute / 60, normalizedMinute % 60).format(TIME_FMT);
    }

    public static int predictMealMinute(List<Integer> mealMinutes) {
        if (mealMinutes == null || mealMinutes.isEmpty()) {
            throw new IllegalArgumentException("Meal minutes cannot be empty");
        }

        int n = mealMinutes.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;

        for (int i = 0; i < n; i++) {
            double x = i + 1;
            double y = mealMinutes.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double denominator = n * sumXX - sumX * sumX;
        if (denominator == 0) {
            return (int) Math.round(sumY / n);
        }

        double a = (n * sumXY - sumX * sumY) / denominator;
        double b = (sumY - a * sumX) / n;
        return (int) Math.round(a * (n + 1) + b);
    }
}
