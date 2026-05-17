package com.caregiver.service;

import com.caregiver.dto.MealScheduleResponse;
import com.caregiver.entity.CaregiverSchedule;
import com.caregiver.entity.MealSchedule;
import com.caregiver.repository.CaregiverScheduleRepository;
import com.caregiver.repository.MealScheduleRepository;
import com.caregiver.util.MealTimeRegressionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public static final ZoneId MYT = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String MEAL_NOTE_PREFIX = "meal:";

    private static final int PREDICTION_HISTORY_WINDOW = 14;
    private static final int MAX_REGRESSION_FORWARD_DAYS = 7;
    private static final int MAX_QUAD_VS_LINEAR_DIFF_MINUTES = 90;
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
        LocalDate todayMyt = LocalDate.now(MYT);
        List<MealScheduleResponse> result = new ArrayList<>();
        for (String mealType : DEFAULTS.keySet()) {
            result.add(new MealScheduleResponse(caregiverId, mealType,
                    resolveEffectiveMealTime(caregiverId, mealType, todayMyt)));
        }
        return result;
    }

    /**
     * Effective meal time for a calendar day: manual override for that MYT day, else regression.
     */
    public String resolveEffectiveMealTime(Long caregiverId, String mealType, LocalDate dayMyt) {
        String upperType = mealType.toUpperCase();
        Optional<MealSchedule> override = findLatestForMytDay(caregiverId, upperType, dayMyt);
        if (override.isPresent()) {
            return override.get().getMealTime().format(TIME_FMT);
        }
        return predictMealTime(caregiverId, upperType, dayMyt);
    }

    public Map<String, String> buildEffectiveMealTimesMap(Long caregiverId, LocalDate dayMyt) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String mealType : DEFAULTS.keySet()) {
            map.put(mealType, resolveEffectiveMealTime(caregiverId, mealType, dayMyt));
        }
        return map;
    }

    @Transactional
    public MealScheduleResponse updateMealTime(Long caregiverId, String mealType, String mealTimeStr) {
        String upperType = mealType.toUpperCase();
        if (mealTimeStr == null || mealTimeStr.isBlank()) {
            throw new IllegalArgumentException("mealTime is required");
        }
        LocalTime mealTime = LocalTime.parse(mealTimeStr.trim(), TIME_FMT);
        LocalDate dayMyt = LocalDate.now(MYT);
        LocalDateTime nowMyt = LocalDateTime.now(MYT);

        MealSchedule entity = findLatestForMytDay(caregiverId, upperType, dayMyt).orElseGet(() -> {
            MealSchedule m = new MealSchedule();
            m.setCaregiverId(caregiverId);
            m.setMealType(upperType);
            return m;
        });
        entity.setMealTime(mealTime);
        entity.setRecordedAt(nowMyt);
        MealSchedule saved = mealScheduleRepository.save(entity);

        syncRemainingWeekMealEntries(caregiverId);

        return toResponse(saved);
    }

    public List<MealScheduleResponse> refreshPredictedMealTimes(Long caregiverId) {
        List<MealScheduleResponse> predictedMeals = getMealSchedules(caregiverId);
        syncRemainingWeekMealEntries(caregiverId);
        return predictedMeals;
    }

    public String predictMealTime(Long caregiverId, String mealType) {
        return predictMealTime(caregiverId, mealType, LocalDate.now(MYT));
    }

    /**
     * Regression prediction for {@code targetDayMyt}, excluding samples logged on that same day
     * so manual overrides do not skew the model when the override is not in effect.
     */
    public String predictMealTime(Long caregiverId, String mealType, LocalDate targetDayMyt) {
        String upperType = mealType.toUpperCase();
        List<MealSchedule> rows = mealScheduleRepository
                .findByCaregiverIdAndMealTypeOrderByIdAsc(caregiverId, upperType);

        if (rows.isEmpty()) {
            return DEFAULTS.getOrDefault(upperType, LocalTime.of(8, 0)).format(TIME_FMT);
        }

        rows.sort(Comparator
                .comparing(MealSchedule::getRecordedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MealSchedule::getId));

        List<MealSchedule> filtered = rows.stream()
                .filter(m -> m.getRecordedAt() == null
                        || !targetDayMyt.equals(m.getRecordedAt().toLocalDate()))
                .toList();

        if (filtered.isEmpty()) {
            return DEFAULTS.getOrDefault(upperType, LocalTime.of(8, 0)).format(TIME_FMT);
        }

        int n = filtered.size();
        int from = Math.max(0, n - PREDICTION_HISTORY_WINDOW);
        List<MealSchedule> window = filtered.subList(from, n);

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
                LocalDate anchorDay = window.get(0).getRecordedAt().toLocalDate();
                List<Double> xDays = new ArrayList<>(window.size());
                for (MealSchedule m : window) {
                    xDays.add((double) ChronoUnit.DAYS.between(anchorDay, m.getRecordedAt().toLocalDate()));
                }
                double xTarget = Math.max(0.0, ChronoUnit.DAYS.between(anchorDay, targetDayMyt));
                double lastX = xDays.get(xDays.size() - 1);
                double xPredict = xTarget;
                if (xPredict > lastX + MAX_REGRESSION_FORWARD_DAYS) {
                    xPredict = lastX + MAX_REGRESSION_FORWARD_DAYS;
                }
                if (lastX > 0 || xDays.stream().anyMatch(x -> x > 0)) {
                    predictedMinute = regressMinuteFromXDays(xDays, windowMinutes, xPredict);
                } else {
                    LocalDateTime anchorDt = window.get(0).getRecordedAt();
                    List<Double> xFrac = new ArrayList<>(window.size());
                    for (MealSchedule m : window) {
                        long minutesFromAnchor = ChronoUnit.MINUTES.between(anchorDt, m.getRecordedAt());
                        xFrac.add(minutesFromAnchor / 1440.0);
                    }
                    double xTargetFrac = Math.max(0.0,
                            ChronoUnit.MINUTES.between(anchorDt, targetDayMyt.atStartOfDay()) / 1440.0);
                    double lastXf = xFrac.get(xFrac.size() - 1);
                    double xPredFrac = xTargetFrac;
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

        List<CaregiverSchedule> existing = caregiverScheduleRepository
                .findByCaregiverIdAndStartDatetimeBetweenAndIsDeleted(caregiverId, weekStart, weekEnd, 0);
        for (CaregiverSchedule cs : existing) {
            if (cs.getScheduleNote() != null && cs.getScheduleNote().startsWith(MEAL_NOTE_PREFIX)) {
                cs.setIsDeleted(1);
                caregiverScheduleRepository.save(cs);
            }
        }

        for (LocalDate day = monday; !day.isAfter(sunday); day = day.plusDays(1)) {
            for (String mealType : DEFAULTS.keySet()) {
                String mealTimeStr = resolveEffectiveMealTime(caregiverId, mealType, day);
                LocalTime mealTime = LocalTime.parse(mealTimeStr, TIME_FMT);
                LocalDateTime start = day.atTime(mealTime);

                CaregiverSchedule cs = new CaregiverSchedule();
                cs.setCaregiverId(caregiverId);
                cs.setScheduleTitle(MEAL_LABELS.getOrDefault(mealType, mealType));
                cs.setStartDatetime(start);
                cs.setEndDatetime(start.plusHours(1));
                cs.setScheduleNote(MEAL_NOTE_PREFIX + mealType);
                cs.setRecurrence("none");
                cs.setIsCompleted(0);
                cs.setIsConflict(0);
                cs.setIsDeleted(0);
                caregiverScheduleRepository.save(cs);
            }
        }
    }

    Optional<MealSchedule> findLatestForMytDay(Long caregiverId, String mealType, LocalDate dayMyt) {
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

    private void syncRemainingWeekMealEntries(Long caregiverId) {
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

        for (CaregiverSchedule cs : weekEntries) {
            if (cs.getScheduleNote() == null || !cs.getScheduleNote().startsWith(MEAL_NOTE_PREFIX)) {
                continue;
            }

            String mealType = cs.getScheduleNote().substring(MEAL_NOTE_PREFIX.length());
            LocalDate entryDate = cs.getStartDatetime().toLocalDate();
            String mealTimeStr = resolveEffectiveMealTime(caregiverId, mealType, entryDate);
            LocalTime mealTime = LocalTime.parse(mealTimeStr, TIME_FMT);

            LocalDateTime newStart = entryDate.atTime(mealTime);
            cs.setStartDatetime(newStart);
            cs.setEndDatetime(newStart.plusHours(1));
            caregiverScheduleRepository.save(cs);
        }
    }
}
