package com.caregiver.service;

import com.caregiver.entity.MealSchedule;
import com.caregiver.repository.CaregiverScheduleRepository;
import com.caregiver.repository.MealScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealScheduleServiceEffectiveTimeTest {

    @Mock
    private MealScheduleRepository mealScheduleRepository;

    @Mock
    private CaregiverScheduleRepository caregiverScheduleRepository;

    @Mock
    private ScheduleCascadeService scheduleCascadeService;

    @InjectMocks
    private MealScheduleService mealScheduleService;

    private static final Long CAREGIVER_ID = 1L;

    @Test
    void resolveEffectiveMealTime_returnsManualOverrideForToday() {
        LocalDate today = LocalDate.now(MealScheduleService.MYT);
        MealSchedule row = new MealSchedule();
        row.setId(10L);
        row.setCaregiverId(CAREGIVER_ID);
        row.setMealType("BREAKFAST");
        row.setMealTime(LocalTime.of(10, 30));
        row.setRecordedAt(today.atTime(9, 0));

        when(mealScheduleRepository.findByCaregiverIdAndMealTypeOrderByIdAsc(CAREGIVER_ID, "BREAKFAST"))
                .thenReturn(List.of(row));

        String effective = mealScheduleService.resolveEffectiveMealTime(CAREGIVER_ID, "BREAKFAST", today);
        assertEquals("10:30", effective);
    }

    @Test
    void resolveEffectiveMealTime_returnsDefaultWhenNoHistory() {
        LocalDate today = LocalDate.now(MealScheduleService.MYT);
        when(mealScheduleRepository.findByCaregiverIdAndMealTypeOrderByIdAsc(eq(CAREGIVER_ID), anyString()))
                .thenReturn(List.of());

        String effective = mealScheduleService.resolveEffectiveMealTime(CAREGIVER_ID, "LUNCH", today);
        assertEquals("13:00", effective);
    }
}
