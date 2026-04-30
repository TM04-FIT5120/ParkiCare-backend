package com.caregiver.service;

import com.caregiver.dto.MealScheduleResponse;
import com.caregiver.entity.CaregiverSchedule;
import com.caregiver.entity.MealSchedule;
import com.caregiver.repository.CaregiverScheduleRepository;
import com.caregiver.repository.MealScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MealScheduleService {

    private final MealScheduleRepository mealScheduleRepository;
    private final CaregiverScheduleRepository caregiverScheduleRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId MYT = ZoneId.of("Asia/Kuala_Lumpur");
    private static final String MEAL_NOTE_PREFIX = "meal:";

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
        Map<String, MealSchedule> saved = new HashMap<>();
        for (MealSchedule ms : mealScheduleRepository.findByCaregiverId(caregiverId)) {
            saved.put(ms.getMealType(), ms);
        }

        List<MealScheduleResponse> result = new ArrayList<>();
        for (Map.Entry<String, LocalTime> entry : DEFAULTS.entrySet()) {
            String mealType = entry.getKey();
            if (saved.containsKey(mealType)) {
                result.add(toResponse(saved.get(mealType)));
            } else {
                result.add(new MealScheduleResponse(null, caregiverId, mealType,
                        entry.getValue().format(TIME_FMT)));
            }
        }
        return result;
    }

    public MealScheduleResponse updateMealTime(Long caregiverId, String mealType, String mealTimeStr) {
        String upperType = mealType.toUpperCase();
        LocalTime mealTime = LocalTime.parse(mealTimeStr, TIME_FMT);

        // Persist to meal_schedule
        MealSchedule entity = mealScheduleRepository
                .findByCaregiverIdAndMealType(caregiverId, upperType)
                .orElse(new MealSchedule(null, caregiverId, upperType, mealTime));
        entity.setMealTime(mealTime);
        MealSchedule saved = mealScheduleRepository.save(entity);

        // Sync remaining caregiver_schedule entries for today..Sunday
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

        String noteKey = MEAL_NOTE_PREFIX + upperType;
        for (CaregiverSchedule cs : weekEntries) {
            if (!noteKey.equals(cs.getScheduleNote())) continue;
            LocalDate entryDate = cs.getStartDatetime().toLocalDate();
            LocalDateTime newStart = entryDate.atTime(mealTime);
            cs.setStartDatetime(newStart);
            cs.setEndDatetime(newStart.plusHours(1));
            caregiverScheduleRepository.save(cs);
        }

        return toResponse(saved);
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

    private MealScheduleResponse toResponse(MealSchedule ms) {
        return new MealScheduleResponse(
                ms.getId(),
                ms.getCaregiverId(),
                ms.getMealType(),
                ms.getMealTime().format(TIME_FMT));
    }
}
