package com.caregiver.service;

import com.caregiver.entity.*;
import com.caregiver.repository.*;
import com.caregiver.util.MealDoseTimeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleCascadeService {

    public static final String SCHEDULE_SOURCE_AI = "AI_RECOMMENDATION";
    public static final String SCHEDULE_SOURCE_MANUAL = "MANUAL";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int ON_WINDOW_START_AFTER_DOSE_MINUTES = 60;
    private static final int ON_WINDOW_END_AFTER_DOSE_MINUTES = 240;
    private static final int MEAL_BUSY_BLOCK_MINUTES = 60;
    private static final LocalTime SLOT_START = LocalTime.of(6, 0);
    private static final LocalTime SLOT_END = LocalTime.of(23, 0);
    private static final int SLOT_STEP_MINUTES = 15;

    private final MealScheduleService mealScheduleService;
    private final MedicationReminderRepository medicationReminderRepository;
    private final DrugBaseRepository drugBaseRepository;
    private final PatientRepository patientRepository;
    private final PatientHomeCareScheduleRepository homeCareScheduleRepository;
    private final PatientOutdoorScheduleRepository outdoorScheduleRepository;
    private final CaregiverScheduleRepository caregiverScheduleRepository;
    private final CaregiverAlertService caregiverAlertService;

    @Transactional
    public void onMealTimeUpdated(Long caregiverId) {
        LocalDate todayMyt = LocalDate.now(MealScheduleService.MYT);
        Patient patient = resolveSinglePatient(caregiverId);

        Map<String, String> mealTimes = mealScheduleService.buildEffectiveMealTimesMap(caregiverId, todayMyt);
        rescheduleMealAnchoredMedications(caregiverId, todayMyt, mealTimes);
        List<LocalTime> medTimes = loadMedicationTimes(patient.getId(), todayMyt);
        rescheduleAiAcceptedActivitiesToday(caregiverId, patient, todayMyt, mealTimes, medTimes);
    }

    private void rescheduleMealAnchoredMedications(
            Long caregiverId,
            LocalDate todayMyt,
            Map<String, String> mealTimes) {

        List<MedicationPlan> plans = medicationReminderRepository.findMealAnchoredActiveByCaregiver(caregiverId, todayMyt);
        Map<Long, List<MedicationPlan>> byPlanId = plans.stream()
                .collect(Collectors.groupingBy(MedicationPlan::getPlanId));

        for (List<MedicationPlan> group : byPlanId.values()) {
            if (group.isEmpty()) {
                continue;
            }
            MedicationPlan sample = group.get(0);
            List<String> anchored = MealDoseTimeCalculator.parseAnchoredMeals(sample.getAnchoredMeals());
            if (anchored.isEmpty()) {
                continue;
            }

            DrugBase drug = drugBaseRepository.findById(sample.getDrugId()).orElse(null);
            int interval = (drug != null && drug.getIntervalMinutes() != null && drug.getIntervalMinutes() > 0)
                    ? drug.getIntervalMinutes()
                    : 60;

            List<LocalTime> newTimes = MealDoseTimeCalculator.calculateDoseTimes(
                    anchored, mealTimes, sample.getMealTiming(), interval);

            if (newTimes.isEmpty()) {
                continue;
            }

            group.sort(Comparator.comparing(MedicationPlan::getAdminTime));
            int assignCount = Math.min(group.size(), newTimes.size());
            for (int i = 0; i < assignCount; i++) {
                MedicationPlan row = group.get(i);
                LocalTime t = newTimes.get(i);
                row.setAdminTime(t);
                row.setRemindTime(t);
                medicationReminderRepository.save(row);
            }
        }
    }

    private void rescheduleAiAcceptedActivitiesToday(
            Long caregiverId,
            Patient patient,
            LocalDate todayMyt,
            Map<String, String> mealTimes,
            List<LocalTime> medTimes) {

        LocalDateTime dayStart = todayMyt.atStartOfDay();
        LocalDateTime dayEnd = todayMyt.plusDays(1).atStartOfDay();

        List<PatientHomeCareSchedule> homeAi = homeCareScheduleRepository
                .findByPatientIdAndIsDeletedAndIsCompletedAndScheduleSourceAndStartDatetimeGreaterThanEqualAndStartDatetimeLessThan(
                        patient.getId(), 0, 0, SCHEDULE_SOURCE_AI, dayStart, dayEnd);

        List<PatientOutdoorSchedule> outdoorAi = outdoorScheduleRepository
                .findByPatientIdAndIsDeletedAndIsCompletedAndScheduleSourceAndStartDatetimeGreaterThanEqualAndStartDatetimeLessThan(
                        patient.getId(), 0, 0, SCHEDULE_SOURCE_AI, dayStart, dayEnd);

        List<TimeRange> staticBlocks = buildStaticBlockedRanges(caregiverId, patient.getId(), todayMyt, mealTimes, null, null);

        for (PatientHomeCareSchedule event : homeAi) {
            rescheduleHomeEvent(caregiverId, event, todayMyt, medTimes, mealTimes, staticBlocks, homeAi, outdoorAi);
        }
        for (PatientOutdoorSchedule event : outdoorAi) {
            rescheduleOutdoorEvent(caregiverId, event, todayMyt, medTimes, mealTimes, staticBlocks, homeAi, outdoorAi);
        }
    }

    private void rescheduleHomeEvent(
            Long caregiverId,
            PatientHomeCareSchedule event,
            LocalDate todayMyt,
            List<LocalTime> medTimes,
            Map<String, String> mealTimes,
            List<TimeRange> staticBlocks,
            List<PatientHomeCareSchedule> allHome,
            List<PatientOutdoorSchedule> allOutdoor) {

        long durationMinutes = ChronoUnit.MINUTES.between(event.getStartDatetime(), event.getEndDatetime());
        if (durationMinutes < 1) {
            durationMinutes = 30;
        }

        String period = safePeriod(event.getAiMedicationPeriod());
        List<TimeRange> blocked = buildBlockedExcluding(staticBlocks, event.getId(), "home", allHome, allOutdoor);

        LocalDateTime newStart = findBestStart(
                todayMyt, period, (int) durationMinutes, medTimes, mealTimes, blocked, event.getStartDatetime());

        LocalDateTime newEnd = newStart.plusMinutes(durationMinutes);
        event.setStartDatetime(newStart);
        event.setEndDatetime(newEnd);
        homeCareScheduleRepository.save(event);

        detectAndAlertOverlaps(caregiverId, event.getId(), "home", event.getHomeCareTitle(), newStart, newEnd,
                patientFromHome(event), todayMyt);
    }

    private void rescheduleOutdoorEvent(
            Long caregiverId,
            PatientOutdoorSchedule event,
            LocalDate todayMyt,
            List<LocalTime> medTimes,
            Map<String, String> mealTimes,
            List<TimeRange> staticBlocks,
            List<PatientHomeCareSchedule> allHome,
            List<PatientOutdoorSchedule> allOutdoor) {

        long durationMinutes = ChronoUnit.MINUTES.between(event.getStartDatetime(), event.getEndDatetime());
        if (durationMinutes < 1) {
            durationMinutes = 30;
        }

        String period = safePeriod(event.getAiMedicationPeriod());
        List<TimeRange> blocked = buildBlockedExcluding(staticBlocks, event.getId(), "outdoor", allHome, allOutdoor);

        LocalDateTime newStart = findBestStart(
                todayMyt, period, (int) durationMinutes, medTimes, mealTimes, blocked, event.getStartDatetime());

        LocalDateTime newEnd = newStart.plusMinutes(durationMinutes);
        event.setStartDatetime(newStart);
        event.setEndDatetime(newEnd);
        outdoorScheduleRepository.save(event);

        detectAndAlertOverlaps(caregiverId, event.getId(), "outdoor", event.getOutdoorTitle(), newStart, newEnd,
                event.getPatientId(), todayMyt);
    }

    private Long patientFromHome(PatientHomeCareSchedule event) {
        return event.getPatientId();
    }

    private void detectAndAlertOverlaps(
            Long caregiverId,
            Long rescheduledId,
            String rescheduledSource,
            String rescheduledTitle,
            LocalDateTime newStart,
            LocalDateTime newEnd,
            Long patientId,
            LocalDate todayMyt) {

        TimeRange rescheduled = new TimeRange(newStart, newEnd);
        Map<String, Object> rescheduledPayload = eventPayload(rescheduledId, rescheduledSource, rescheduledTitle, newStart, newEnd);

        LocalDateTime dayStart = todayMyt.atStartOfDay();
        LocalDateTime dayEnd = todayMyt.plusDays(1).atStartOfDay();

        for (PatientHomeCareSchedule h : homeCareScheduleRepository
                .findByPatientIdAndIsDeleted(patientId, 0)) {
            if (h.getId().equals(rescheduledId) && "home".equals(rescheduledSource)) {
                continue;
            }
            if (h.getIsCompleted() != null && h.getIsCompleted() == 1) {
                continue;
            }
            if (SCHEDULE_SOURCE_MANUAL.equalsIgnoreCase(safe(h.getScheduleSource()))
                    && overlaps(rescheduled, h.getStartDatetime(), h.getEndDatetime())) {
                caregiverAlertService.createOverlapAlert(
                        caregiverId,
                        "manual_patient",
                        rescheduledPayload,
                        eventPayload(h.getId(), "home", h.getHomeCareTitle(), h.getStartDatetime(), h.getEndDatetime()));
            }
        }

        for (PatientOutdoorSchedule o : outdoorScheduleRepository.findByPatientIdAndIsDeleted(patientId, 0)) {
            if (o.getId().equals(rescheduledId) && "outdoor".equals(rescheduledSource)) {
                continue;
            }
            if (o.getIsCompleted() != null && o.getIsCompleted() == 1) {
                continue;
            }
            if (SCHEDULE_SOURCE_MANUAL.equalsIgnoreCase(safe(o.getScheduleSource()))
                    && overlaps(rescheduled, o.getStartDatetime(), o.getEndDatetime())) {
                caregiverAlertService.createOverlapAlert(
                        caregiverId,
                        "manual_patient",
                        rescheduledPayload,
                        eventPayload(o.getId(), "outdoor", o.getOutdoorTitle(), o.getStartDatetime(), o.getEndDatetime()));
            }
        }

        List<CaregiverSchedule> caregiverEvents = caregiverScheduleRepository
                .findByCaregiverIdAndStartDatetimeBetweenAndIsDeleted(
                        caregiverId, dayStart, dayEnd.minusSeconds(1), 0);

        for (CaregiverSchedule cs : caregiverEvents) {
            if (overlaps(rescheduled, cs.getStartDatetime(), cs.getEndDatetime())) {
                caregiverAlertService.createOverlapAlert(
                        caregiverId,
                        "caregiver",
                        rescheduledPayload,
                        eventPayload(cs.getId(), "caregiver", cs.getScheduleTitle(),
                                cs.getStartDatetime(), cs.getEndDatetime()));
            }
        }
    }

    private Map<String, Object> eventPayload(
            Long id, String source, String title, LocalDateTime start, LocalDateTime end) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("source", source);
        m.put("title", title);
        m.put("start", start.format(DATETIME_FMT));
        m.put("end", end.format(DATETIME_FMT));
        return m;
    }

    private List<TimeRange> buildStaticBlockedRanges(
            Long caregiverId,
            Long patientId,
            LocalDate todayMyt,
            Map<String, String> mealTimes,
            Long excludeHomeId,
            Long excludeOutdoorId) {

        List<TimeRange> blocked = new ArrayList<>();
        for (String mealTime : mealTimes.values()) {
            LocalTime start = LocalTime.parse(mealTime, TIME_FMT);
            LocalDateTime mealStart = todayMyt.atTime(start);
            blocked.add(new TimeRange(mealStart, mealStart.plusMinutes(MEAL_BUSY_BLOCK_MINUTES)));
        }

        LocalDateTime dayStart = todayMyt.atStartOfDay();
        LocalDateTime dayEnd = todayMyt.plusDays(1).atStartOfDay();

        for (PatientHomeCareSchedule h : homeCareScheduleRepository.findByPatientIdAndIsDeleted(patientId, 0)) {
            if (h.getIsCompleted() != null && h.getIsCompleted() == 1) {
                continue;
            }
            if (excludeHomeId != null && excludeHomeId.equals(h.getId())) {
                continue;
            }
            if (SCHEDULE_SOURCE_MANUAL.equalsIgnoreCase(safe(h.getScheduleSource()))
                    && isOnDay(h.getStartDatetime(), dayStart, dayEnd)) {
                blocked.add(new TimeRange(h.getStartDatetime(), h.getEndDatetime()));
            }
        }

        for (PatientOutdoorSchedule o : outdoorScheduleRepository.findByPatientIdAndIsDeleted(patientId, 0)) {
            if (o.getIsCompleted() != null && o.getIsCompleted() == 1) {
                continue;
            }
            if (excludeOutdoorId != null && excludeOutdoorId.equals(o.getId())) {
                continue;
            }
            if (SCHEDULE_SOURCE_MANUAL.equalsIgnoreCase(safe(o.getScheduleSource()))
                    && isOnDay(o.getStartDatetime(), dayStart, dayEnd)) {
                blocked.add(new TimeRange(o.getStartDatetime(), o.getEndDatetime()));
            }
        }

        for (CaregiverSchedule cs : caregiverScheduleRepository
                .findByCaregiverIdAndStartDatetimeBetweenAndIsDeleted(caregiverId, dayStart, dayEnd.minusSeconds(1), 0)) {
            blocked.add(new TimeRange(cs.getStartDatetime(), cs.getEndDatetime()));
        }

        return blocked;
    }

    private List<TimeRange> buildBlockedExcluding(
            List<TimeRange> base,
            Long excludeId,
            String excludeType,
            List<PatientHomeCareSchedule> allHome,
            List<PatientOutdoorSchedule> allOutdoor) {

        List<TimeRange> blocked = new ArrayList<>(base);
        for (PatientHomeCareSchedule h : allHome) {
            if ("home".equals(excludeType) && excludeId.equals(h.getId())) {
                continue;
            }
            blocked.add(new TimeRange(h.getStartDatetime(), h.getEndDatetime()));
        }
        for (PatientOutdoorSchedule o : allOutdoor) {
            if ("outdoor".equals(excludeType) && excludeId.equals(o.getId())) {
                continue;
            }
            blocked.add(new TimeRange(o.getStartDatetime(), o.getEndDatetime()));
        }
        return blocked;
    }

    private LocalDateTime findBestStart(
            LocalDate todayMyt,
            String period,
            int durationMinutes,
            List<LocalTime> medTimes,
            Map<String, String> mealTimes,
            List<TimeRange> blocked,
            LocalDateTime preferredStart) {

        LocalDateTime best = null;
        long bestDistance = Long.MAX_VALUE;

        for (LocalDateTime candidate = todayMyt.atTime(SLOT_START);
             !candidate.toLocalTime().isAfter(SLOT_END.minusMinutes(durationMinutes));
             candidate = candidate.plusMinutes(SLOT_STEP_MINUTES)) {

            LocalDateTime candidateEnd = candidate.plusMinutes(durationMinutes);
            if (candidateEnd.toLocalTime().isAfter(SLOT_END) && !candidateEnd.toLocalDate().isAfter(todayMyt)) {
                continue;
            }

            if (!isInMedicationPeriod(candidate.toLocalTime(), todayMyt, period, medTimes)) {
                continue;
            }

            TimeRange candidateRange = new TimeRange(candidate, candidateEnd);
            if (blocked.stream().anyMatch(b -> b.overlaps(candidateRange))) {
                continue;
            }

            long dist = Math.abs(ChronoUnit.MINUTES.between(preferredStart, candidate));
            if (dist < bestDistance) {
                bestDistance = dist;
                best = candidate;
            }
        }

        if (best != null) {
            return best;
        }

        for (LocalDateTime candidate = todayMyt.atTime(SLOT_START);
             !candidate.toLocalTime().isAfter(SLOT_END.minusMinutes(durationMinutes));
             candidate = candidate.plusMinutes(SLOT_STEP_MINUTES)) {

            LocalDateTime candidateEnd = candidate.plusMinutes(durationMinutes);
            if (!isInMedicationPeriod(candidate.toLocalTime(), todayMyt, period, medTimes)) {
                continue;
            }
            long dist = Math.abs(ChronoUnit.MINUTES.between(preferredStart, candidate));
            if (dist < bestDistance) {
                bestDistance = dist;
                best = candidate;
            }
        }

        return best != null ? best : preferredStart;
    }

    private boolean isInMedicationPeriod(
            LocalTime startTime, LocalDate scheduleDay, String period, List<LocalTime> medTimes) {
        boolean inOn = false;
        LocalDateTime rec = LocalDateTime.of(scheduleDay, startTime);
        for (LocalTime dose : medTimes) {
            LocalDateTime doseDt = LocalDateTime.of(scheduleDay, dose);
            LocalDateTime winStart = doseDt.plusMinutes(ON_WINDOW_START_AFTER_DOSE_MINUTES);
            LocalDateTime winEnd = doseDt.plusMinutes(ON_WINDOW_END_AFTER_DOSE_MINUTES);
            if (!rec.isBefore(winStart) && rec.isBefore(winEnd)) {
                inOn = true;
                break;
            }
        }
        return "on".equals(period) ? inOn : !inOn;
    }

    private List<LocalTime> loadMedicationTimes(Long patientId, LocalDate todayMyt) {
        return medicationReminderRepository.findAllPlansForPatientOnCalendarDay(patientId, todayMyt).stream()
                .map(MedicationPlan::getRemindTime)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private Patient resolveSinglePatient(Long caregiverId) {
        List<Patient> patients = patientRepository.findByCaregiverId(caregiverId);
        if (patients.isEmpty()) {
            throw new RuntimeException("Patient not found for this caregiver");
        }
        if (patients.size() > 1) {
            throw new RuntimeException("Multiple patients found for this caregiver");
        }
        return patients.get(0);
    }

    private static boolean overlaps(TimeRange a, LocalDateTime start, LocalDateTime end) {
        return a.overlaps(new TimeRange(start, end));
    }

    private static boolean isOnDay(LocalDateTime dt, LocalDateTime dayStart, LocalDateTime dayEnd) {
        return !dt.isBefore(dayStart) && dt.isBefore(dayEnd);
    }

    private static String safePeriod(String period) {
        String p = period == null ? "off" : period.trim().toLowerCase(Locale.ROOT);
        return "on".equals(p) ? "on" : "off";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end) {
        boolean overlaps(TimeRange other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }
}
