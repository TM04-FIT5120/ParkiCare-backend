package com.caregiver.service;

import com.caregiver.dto.MealScheduleResponse;
import com.caregiver.entity.CaregiverSchedule;
import com.caregiver.entity.MealSchedule;
import com.caregiver.repository.CaregiverScheduleRepository;
import com.caregiver.repository.MealScheduleRepository;
import com.caregiver.util.MealTimeRegressionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MealScheduleService {

    private final MealScheduleRepository mealScheduleRepository;
    private final CaregiverScheduleRepository caregiverScheduleRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId MYT = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String MEAL_NOTE_PREFIX = "meal:";

    /** Use only the latest N logged times for prediction. */
    private static final int PREDICTION_HISTORY_WINDOW = 14;

    /** Cap forward extrapolation (days beyond last sample) when using day-axis regression. */
    private static final int MAX_REGRESSION_FORWARD_DAYS = 7;

    /** If quadratic prediction differs from linear by more than this, use linear (minutes). */
    private static final int MAX_QUAD_VS_LINEAR_DIFF_MINUTES = 90;

    /**
     * If |c2| in y≈c0+c1*x+c2*x^2 exceeds this (minutes per day²), quadratic extrapolation is too sharp.
     */
    private static final double MAX_QUAD_CURVATURE = 2.0;

    private static final Map<String, LocalTime> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("BREAKFAST", LocalTime.of(8, 0));
        DEFAULTS.put("LUNCH", LocalTime.of(13, 0));
        DEFAULTS.put("DINNER", LocalTime.of(19, 0));
    }

    private static final Map<String, String> MEAL_LABELS = Map.of(
            "BREAKFAST", "Breakfast",
            "LUNCH", "Lunch",
            "DINNER", "Dinner"
    );

    public List<MealScheduleResponse> getMealSchedules(Long caregiverId) {
        List<MealScheduleResponse> result = new ArrayList<>();
        for (String mealType : DEFAULTS.keySet()) {
            result.add(new MealScheduleResponse(caregiverId, mealType,
                    predictMealTime(caregiverId, mealType)));
        }
        return result;
    }


/**
 * update the meal time for a caregiver caregiver.
 * @param caregiverId The ID of the caregiver caregiver.
 * @param mealType The meal type to update.
 * @param mealTimeStr The meal time string in HH:mm format.
 * @return The updated meal schedule response.
 * @throws IllegalArgumentException If mealTime is null or blank.
 */
    public MealScheduleResponse updateMealTime(Long caregiverId, String mealType, String mealTimeStr) {
    // 将用餐类型转换为大写
        String upperType = mealType.toUpperCase();
    // 检查用餐时间字符串是否为空
        if (mealTimeStr == null || mealTimeStr.isBlank()) {
            throw new IllegalArgumentException("mealTime is required");
        }
    // 解析用餐时间字符串为LocalTime对象
        LocalTime mealTime = LocalTime.parse(mealTimeStr.trim(), TIME_FMT);
    // 获取马来西亚时区的当前日期
        LocalDate dayMyt = LocalDate.now(MYT);
    // 获取马来西亚时区的当前日期时间
        LocalDateTime nowMyt = LocalDateTime.now(MYT);

        // At most one row per (caregiver, meal type, MYT calendar day): same-day edits overwrite.
        // Resolve in Java (not native DATE bind) to avoid JDBC/LocalDate mismatch with MySQL DATE().
        MealSchedule entity = findLatestForMytDay(caregiverId, upperType, dayMyt).orElseGet(() -> {
            MealSchedule m = new MealSchedule();
            m.setCaregiverId(caregiverId);
            m.setMealType(upperType);
            return m;
        });
        entity.setMealTime(mealTime);
        entity.setRecordedAt(nowMyt);
        MealSchedule saved = mealScheduleRepository.save(entity);

        syncRemainingWeekMealEntries(caregiverId, List.of(toResponse(saved)));

        return toResponse(saved);
    }

    public List<MealScheduleResponse> refreshPredictedMealTimes(Long caregiverId) {
        List<MealScheduleResponse> predictedMeals = getMealSchedules(caregiverId);
        syncRemainingWeekMealEntries(caregiverId, predictedMeals);
        return predictedMeals;
    }

    public String predictMealTime(Long caregiverId, String mealType) {
        String upperType = mealType.toUpperCase();
        List<MealSchedule> rows = mealScheduleRepository
                .findByCaregiverIdAndMealTypeOrderByIdAsc(caregiverId, upperType);

        if (rows.isEmpty()) {
            return DEFAULTS.getOrDefault(upperType, LocalTime.of(8, 0)).format(TIME_FMT);
        }

        rows.sort(Comparator
                .comparing(MealSchedule::getRecordedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MealSchedule::getId));

        int n = rows.size();
        int from = Math.max(0, n - PREDICTION_HISTORY_WINDOW);
        List<MealSchedule> window = rows.subList(from, n);

        List<Integer> windowMinutes = new ArrayList<>(window.size());
        for (MealSchedule meal : window) {
            windowMinutes.add(MealTimeRegressionUtil.timeToMinute(meal.getMealTime().format(TIME_FMT)));
        }

        int predictedMinute;
        if (windowMinutes.size() <= 2) {
            predictedMinute = averageMealMinute(windowMinutes);
        } else {
            boolean allHaveRecordedAt = window.stream().allMatch(m -> m.getRecordedAt() != null);
            if (allHaveRecordedAt) {
                // x = whole days between first sample's calendar day and each recorded_at (MYT dates stored as wall clock).
                LocalDate anchorDay = window.get(0).getRecordedAt().toLocalDate();
                List<Double> xDays = new ArrayList<>(window.size());
                for (MealSchedule m : window) {
                    xDays.add((double) ChronoUnit.DAYS.between(anchorDay, m.getRecordedAt().toLocalDate()));
                }
                LocalDate todayMyt = LocalDate.now(MYT);
                double xToday = Math.max(0.0, ChronoUnit.DAYS.between(anchorDay, todayMyt));
                double lastX = xDays.get(xDays.size() - 1);
                double xPredict = xToday;
                if (xPredict > lastX + MAX_REGRESSION_FORWARD_DAYS) {
                    xPredict = lastX + MAX_REGRESSION_FORWARD_DAYS;
                }
                if (lastX > 0 || xDays.stream().anyMatch(x -> x > 0)) {
                    predictedMinute = regressMinuteFromXDays(xDays, windowMinutes, xPredict);
                } else {
                    // All samples same calendar day: spread x by time-of-day within that day
                    LocalDateTime anchorDt = window.get(0).getRecordedAt();
                    List<Double> xFrac = new ArrayList<>(window.size());
                    for (MealSchedule m : window) {
                        long minutesFromAnchor = ChronoUnit.MINUTES.between(anchorDt, m.getRecordedAt());
                        xFrac.add(minutesFromAnchor / 1440.0);
                    }
                    double xTodayFrac = Math.max(0.0, ChronoUnit.MINUTES.between(anchorDt, LocalDateTime.now(MYT)) / 1440.0);
                    double lastXf = xFrac.get(xFrac.size() - 1);
                    double xPredFrac = xTodayFrac;
                    if (xPredFrac > lastXf + MAX_REGRESSION_FORWARD_DAYS) {
                        xPredFrac = lastXf + MAX_REGRESSION_FORWARD_DAYS;
                    }
                    predictedMinute = regressMinuteFromXDays(xFrac, windowMinutes, xPredFrac);
                }
            } else {
                predictedMinute = modeMealMinute(new ArrayList<>(windowMinutes));
            }
        }

        return MealTimeRegressionUtil.minuteToTime(predictedMinute);
    }

    public void generateWeeklyMeals(Long caregiverId) {
        LocalDate today = LocalDate.now(MYT);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        LocalDateTime weekStart = monday.atStartOfDay();
        LocalDateTime weekEnd = sunday.atTime(23, 59, 59);

        // Soft-delete ALL existing meal entries for this week first.
        // This cleans up any duplicates from previous runs before recreating cleanly.
        List<CaregiverSchedule> existing = caregiverScheduleRepository
                .findByCaregiverIdAndStartDatetimeBetweenAndIsDeleted(caregiverId, weekStart, weekEnd, 0);
        for (CaregiverSchedule cs : existing) {
            if (cs.getScheduleNote() != null && cs.getScheduleNote().startsWith(MEAL_NOTE_PREFIX)) {
                cs.setIsDeleted(1);
                caregiverScheduleRepository.save(cs);
            }
        }

        // Recreate exactly 7 × 3 = 21 entries for this week
        List<MealScheduleResponse> meals = getMealSchedules(caregiverId);
        for (LocalDate day = monday; !day.isAfter(sunday); day = day.plusDays(1)) {
            for (MealScheduleResponse meal : meals) {
                LocalTime mealTime = LocalTime.parse(meal.getMealTime(), TIME_FMT);
                LocalDateTime start = day.atTime(mealTime);

                CaregiverSchedule cs = new CaregiverSchedule();
                cs.setCaregiverId(caregiverId);
                cs.setScheduleTitle(MEAL_LABELS.getOrDefault(meal.getMealType(), meal.getMealType()));
                cs.setStartDatetime(start);
                cs.setEndDatetime(start.plusHours(1));
                cs.setScheduleNote(MEAL_NOTE_PREFIX + meal.getMealType());
                cs.setRecurrence("none");
                cs.setIsCompleted(0);
                cs.setIsConflict(0);
                cs.setIsDeleted(0);
                caregiverScheduleRepository.save(cs);
            }
        }
    }

    /**
     * Latest row for this caregiver + meal type whose {@code recorded_at} falls on {@code dayMyt}
     * (wall-clock date of the stored {@link LocalDateTime}, aligned with {@link #MYT} “today” logic).
     */
    private Optional<MealSchedule> findLatestForMytDay(Long caregiverId, String mealType, LocalDate dayMyt) {
        return mealScheduleRepository.findByCaregiverIdAndMealTypeOrderByIdAsc(caregiverId, mealType).stream()
                .filter(m -> m.getRecordedAt() != null && dayMyt.equals(m.getRecordedAt().toLocalDate()))
                .max(Comparator.comparing(MealSchedule::getId));
    }

    private MealScheduleResponse toResponse(MealSchedule ms) {
        return new MealScheduleResponse(
                ms.getCaregiverId(),
                ms.getMealType(),
                ms.getMealTime().format(TIME_FMT));
    }

    private int averageMealMinute(List<Integer> mealMinutes) {
        return (int) Math.round(mealMinutes.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0));
    }

    /**
     * 众数（分钟数）：出现次数最多的用餐时刻；若并列多只取这些众数的算术平均（四舍五入）；
     * 若每个样本分钟都不同（无众数），退化为与 {@link #averageMealMinute} 相同的均值。
     */
    private int modeMealMinute(List<Integer> mealMinutes) {
        if (mealMinutes == null || mealMinutes.isEmpty()) {
            throw new IllegalArgumentException("Meal minutes cannot be empty");
        }
        Map<Integer, Integer> freq = new HashMap<>();
        for (int m : mealMinutes) {
            freq.merge(m, 1, Integer::sum);
        }
        int maxCount = freq.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> modes = freq.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (maxCount == 1 && modes.size() == mealMinutes.size()) {
            return averageMealMinute(mealMinutes);
        }
        return (int) Math.round(modes.stream().mapToInt(Integer::intValue).average().orElse(modes.get(0)));
    }

    /** Linear / quadratic regression on x vs meal minutes, with mode fallbacks when regression is unusable. */
    private int regressMinuteFromXDays(List<Double> xDays, List<Integer> windowMinutes, double xPredict) {
        OptionalInt linear = MealTimeRegressionUtil.predictLinearMinute(xDays, windowMinutes, xPredict);
        if (windowMinutes.size() >= 4) {
            Optional<MealTimeRegressionUtil.QuadraticFit> quad =
                    MealTimeRegressionUtil.fitQuadraticLeastSquares(xDays, windowMinutes);
            if (quad.isPresent()) {
                double qRaw = quad.get().predict(xPredict);
                if (Double.isFinite(qRaw)) {
                    int qPred = (int) Math.round(qRaw);
                    boolean quadOk = Math.abs(quad.get().getC2()) <= MAX_QUAD_CURVATURE;
                    if (linear.isPresent()) {
                        quadOk = quadOk
                                && Math.abs(qPred - linear.getAsInt()) <= MAX_QUAD_VS_LINEAR_DIFF_MINUTES;
                    }
                    if (quadOk) {
                        return qPred;
                    }
                    if (linear.isPresent()) {
                        return linear.getAsInt();
                    }
                    return modeMealMinute(new ArrayList<>(windowMinutes));
                }
                if (linear.isPresent()) {
                    return linear.getAsInt();
                }
                return modeMealMinute(new ArrayList<>(windowMinutes));
            }
            if (linear.isPresent()) {
                return linear.getAsInt();
            }
            return modeMealMinute(new ArrayList<>(windowMinutes));
        }
        if (linear.isPresent()) {
            return linear.getAsInt();
        }
        return modeMealMinute(new ArrayList<>(windowMinutes));
    }

    private void syncRemainingWeekMealEntries(Long caregiverId, List<MealScheduleResponse> meals) {
        LocalDate today = LocalDate.now(MYT);
        LocalDate sunday = today.getDayOfWeek() == DayOfWeek.SUNDAY
                ? today
                : today.with(DayOfWeek.SUNDAY);

        List<CaregiverSchedule> weekEntries = caregiverScheduleRepository
                .findByCaregiverIdAndStartDatetimeBetweenAndIsDeleted(
                        caregiverId,
                        today.atStartOfDay(),
                        sunday.atTime(23, 59, 59),
                        0);

        Map<String, LocalTime> predictedTimes = new HashMap<>();
        for (MealScheduleResponse meal : meals) {
            predictedTimes.put(meal.getMealType(), LocalTime.parse(meal.getMealTime(), TIME_FMT));
        }

        for (CaregiverSchedule cs : weekEntries) {
            if (cs.getScheduleNote() == null || !cs.getScheduleNote().startsWith(MEAL_NOTE_PREFIX)) {
                continue;
            }

            String mealType = cs.getScheduleNote().substring(MEAL_NOTE_PREFIX.length());
            LocalTime mealTime = predictedTimes.get(mealType);
            if (mealTime == null) {
                continue;
            }

            LocalDate entryDate = cs.getStartDatetime().toLocalDate();
            LocalDateTime newStart = entryDate.atTime(mealTime);
            cs.setStartDatetime(newStart);
            cs.setEndDatetime(newStart.plusHours(1));
            caregiverScheduleRepository.save(cs);
        }
    }
}
