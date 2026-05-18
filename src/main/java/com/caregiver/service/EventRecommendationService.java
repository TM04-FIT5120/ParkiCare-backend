package com.caregiver.service;

import com.caregiver.dto.EventRecommendationItem;
import com.caregiver.dto.EventRecommendationRequest;
import com.caregiver.dto.EventRecommendationResponse;
import com.caregiver.dto.RecommendationFeedbackCreateRequest;
import com.caregiver.dto.WeatherResult;
import com.caregiver.entity.CaregiverSchedule;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.entity.Patient;
import com.caregiver.entity.PatientHomeCareSchedule;
import com.caregiver.entity.PatientOutdoorSchedule;
import com.caregiver.entity.UserEventRecommendationLog;
import com.caregiver.repository.CaregiverScheduleRepository;
import com.caregiver.repository.PatientHomeCareScheduleRepository;
import com.caregiver.repository.PatientOutdoorScheduleRepository;
import com.caregiver.repository.PatientRepository;
import com.caregiver.repository.UserEventRecommendationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventRecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(EventRecommendationService.class);

    private final PatientRepository patientRepository;
    private final MedicationPlanService medicationPlanService;
    private final PatientHomeCareScheduleRepository patientHomeCareScheduleRepository;
    private final PatientOutdoorScheduleRepository patientOutdoorScheduleRepository;
    private final CaregiverScheduleRepository caregiverScheduleRepository;
    private final MealScheduleService mealScheduleService;
    private final WeatherService weatherService;
    private final QwenChatClient qwenChatClient;
    private final UserEventRecommendationLogRepository userEventRecommendationLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Handles model output times such as 6:00 or 09:15:00; when strict HH:mm parsing fails, no longer falls back to the same 06:00 for every entry. */
    private static final DateTimeFormatter[] AI_TIME_FORMATTERS = buildAiTimeFormatters();
    /** Allowed clock window for recommendation start times (aligned with caregiver daytime care). */
    private static final LocalTime REC_SLOT_START_INCLUSIVE = LocalTime.of(6, 0);
    private static final LocalTime REC_SLOT_END_EXCLUSIVE = LocalTime.of(23, 0);
    /** Simplified PD medication-related "on" window: from (dose + 1h) inclusive to (dose + 4h) exclusive. Must match prompt section 6. */
    private static final int ON_WINDOW_START_AFTER_DOSE_MINUTES = 60;
    private static final int ON_WINDOW_END_AFTER_DOSE_MINUTES = 240;
    /** Predicted meal start times extend as busy blocks for prompt / overlap avoidance. */
    private static final int MEAL_BUSY_BLOCK_MINUTES = 60;

    // Generate recommendations and return AI JSON result.
    @Transactional
    public EventRecommendationResponse generateRecommendations(EventRecommendationRequest request) {
        List<Patient> patients = patientRepository.findByCaregiverId(request.getCaregiverId());
        if (patients.isEmpty()) {
            throw new RuntimeException("Patient not found for this caregiver");
        }
        if (patients.size() > 1) {
            throw new RuntimeException("Multiple patients found for this caregiver");
        }
        Patient patient = patients.get(0);
        Long patientId = patient.getId();

        LocalDateTime now = LocalDateTime.now();
        WeatherResult weatherResult = weatherService.getWeatherAndAQI(request.getLat(), request.getLon());

        LocalDate todayMyt = now.toLocalDate();
        String breakfastTime = mealScheduleService.resolveEffectiveMealTime(
                request.getCaregiverId(), "BREAKFAST", todayMyt);
        String lunchTime = mealScheduleService.resolveEffectiveMealTime(
                request.getCaregiverId(), "LUNCH", todayMyt);
        String dinnerTime = mealScheduleService.resolveEffectiveMealTime(
                request.getCaregiverId(), "DINNER", todayMyt);

        List<String> mealBusyWindows = buildMealBusyWindows(now.toLocalDate(), breakfastTime, lunchTime, dinnerTime);

        List<String> medicationTimeList = buildMedicationTimeList(patientId, now.toLocalDate());
        List<String> userOccupiedTimeList = buildUserOccupiedTimeList(
                request.getCaregiverId(), patientId, now);

        LocalDate fromDate = now.toLocalDate().minusDays(14);
        // 5 entries each: accept takes highest score; reject takes lowest score; ties broken by most recent id first. Each entry includes weather snapshot at feedback time.
        List<String> acceptHistoryList = userEventRecommendationLogRepository
                .findTop5AcceptByScoreDesc(request.getCaregiverId(), "accept", fromDate)
                .stream()
                .map(this::formatHistoryLine)
                .toList();

        List<String> rejectHistoryList = userEventRecommendationLogRepository
                .findTop5RejectByScoreAsc(request.getCaregiverId(), "reject", fromDate)
                .stream()
                .map(this::formatHistoryLine)
                .toList();

        String prompt = buildPrompt(
                now,
                weatherResult,
                mealBusyWindows,
                medicationTimeList,
                userOccupiedTimeList,
                acceptHistoryList,
                rejectHistoryList
        );

        logger.info("[EventRecommendation] generate — caregiverId={}, patientId={}, promptLength={}\n{}",
                request.getCaregiverId(), patientId, prompt.length(), prompt);

        try {
            String aiJson = qwenChatClient.generateJsonByPrompt(prompt);
            EventRecommendationResponse response =
                    objectMapper.readValue(aiJson, EventRecommendationResponse.class);

            if (response.getEventRecommendations() == null || response.getEventRecommendations().isEmpty()) {
                throw new RuntimeException("AI returned empty recommendations");
            }

            List<EventRecommendationItem> normalized = normalizeRecommendations(
                    response.getEventRecommendations(), now, medicationTimeList);
            if (normalized.size() != 5) {
                throw new RuntimeException("AI must return exactly 5 recommendations");
            }
            response.setEventRecommendations(normalized);
            return response;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI recommendation result: " + e.getMessage());
        }
    }

    // Save one feedback log only when user accepts/rejects.
    @Transactional
    public UserEventRecommendationLog createFeedbackLog(RecommendationFeedbackCreateRequest request) {
        String normalized = request.getUserFeedback() == null
                ? ""
                : request.getUserFeedback().trim().toLowerCase(Locale.ROOT);
        if (!"accept".equals(normalized) && !"reject".equals(normalized)) {
            throw new RuntimeException("userFeedback must be accept or reject");
        }
        String period = safe(request.getPeriod()).toLowerCase(Locale.ROOT);
        if (!"on".equals(period) && !"off".equals(period)) {
            throw new RuntimeException("period must be on or off");
        }
        String eventType = safe(request.getType()).toLowerCase(Locale.ROOT);
        if (!"home_care".equals(eventType) && !"outdoor".equals(eventType)) {
            throw new RuntimeException("type must be home_care or outdoor");
        }

        WeatherResult weatherResult = weatherService.getWeatherAndAQI(request.getLat(), request.getLon());
        LocalDate eventDate = resolveEventDateByStartTime(request.getStartTime(), LocalDate.now());
        LocalDateTime startDateTime = resolveStartDateTime(request.getStartTime(), eventDate);
        LocalDateTime endDateTime = startDateTime.plusMinutes(request.getDurationMinutes());

        List<Patient> patients = patientRepository.findByCaregiverId(request.getCaregiverId());
        if (patients.isEmpty()) {
            throw new RuntimeException("Patient not found for this caregiver");
        }
        if (patients.size() > 1) {
            throw new RuntimeException("Multiple patients found for this caregiver");
        }
        Long patientId = patients.get(0).getId();

        UserEventRecommendationLog log = new UserEventRecommendationLog();
        log.setCaregiverId(request.getCaregiverId());
        log.setAqi(weatherResult.getAqi());
        log.setWeather(weatherResult.getWeather());
        log.setTemperature(weatherResult.getTemperature());
        log.setEventName(safe(request.getEventName()));
        log.setMedicationPeriod(period);
        log.setEventType(eventType);
        log.setEventDate(eventDate);
        log.setRemark(safe(request.getRemark()));
        log.setUserFeedback(normalized);
        log.setScore("accept".equals(normalized) ? 8 : -5);
        UserEventRecommendationLog savedLog = userEventRecommendationLogRepository.save(log);

        if ("accept".equals(normalized)) {
            saveAcceptedEventToSchedules(
                    patientId,
                    safe(request.getEventName()),
                    eventType,
                    safe(request.getRemark()),
                    startDateTime,
                    endDateTime,
                    period
            );
        }
        return savedLog;
    }

    /**
     * Today's medication reminder times (HH:mm) for the full day, consistent with
     * {@link MedicationPlanService#getTodayAllPlansByPatient}; both is_valid 0 and 1 are included
     * (covering completed and pending plans), not truncated by the current time.
     */
    private List<String> buildMedicationTimeList(Long patientId, LocalDate today) {
        return medicationPlanService.getTodayAllPlansByPatient(patientId, today).stream()
                .map(MedicationPlan::getRemindTime)
                .filter(t -> t != null)
                .distinct()
                .sorted()
                .map(t -> t.format(TIME_FMT))
                .toList();
    }

    /**
     * Three meals: each entry is a busy block of {@link #MEAL_BUSY_BLOCK_MINUTES} minutes starting
     * from the predicted meal start time HH:mm (used in the prompt and for overlap avoidance).
     */
    private List<String> buildMealBusyWindows(LocalDate day, String breakfastHm, String lunchHm, String dinnerHm) {
        List<String> out = new ArrayList<>();
        out.add(formatMealBusyWindow(day, "breakfast", breakfastHm));
        out.add(formatMealBusyWindow(day, "lunch", lunchHm));
        out.add(formatMealBusyWindow(day, "dinner", dinnerHm));
        return out;
    }

    private String formatMealBusyWindow(LocalDate day, String label, String hhmmRaw) {
        if (hhmmRaw == null || hhmmRaw.isBlank()) {
            return label + " (meal " + MEAL_BUSY_BLOCK_MINUTES + "min): (no predicted start)";
        }
        Optional<LocalTime> t = parseFlexibleHm(hhmmRaw);
        if (t.isEmpty()) {
            return label + " (meal " + MEAL_BUSY_BLOCK_MINUTES + "min): (unparsed time \"" + safe(hhmmRaw) + "\")";
        }
        LocalDateTime start = LocalDateTime.of(day, t.get());
        LocalDateTime end = start.plusMinutes(MEAL_BUSY_BLOCK_MINUTES);
        return label + " (meal " + MEAL_BUSY_BLOCK_MINUTES + "min): "
                + start.format(DATETIME_FMT) + " ~ " + end.format(DATETIME_FMT);
    }

    // Merge all occupied schedule ranges to prevent overlap in prompt.
    private List<String> buildUserOccupiedTimeList(Long caregiverId, Long patientId, LocalDateTime now) {
        List<String> occupied = new ArrayList<>();

        List<PatientHomeCareSchedule> homeCares = patientHomeCareScheduleRepository
                .findByPatientIdAndIsDeleted(patientId, 0);
        for (PatientHomeCareSchedule item : homeCares) {
            if (item.getEndDatetime() != null && item.getEndDatetime().isAfter(now)) {
                occupied.add(formatOccupiedSlot(
                        item.getStartDatetime(), item.getEndDatetime(), "home_care", item.getHomeCareTitle()));
            }
        }

        List<PatientOutdoorSchedule> outdoors = patientOutdoorScheduleRepository
                .findByPatientIdAndIsDeleted(patientId, 0);
        for (PatientOutdoorSchedule item : outdoors) {
            if (item.getEndDatetime() != null && item.getEndDatetime().isAfter(now)) {
                occupied.add(formatOccupiedSlot(
                        item.getStartDatetime(), item.getEndDatetime(), "outdoor", item.getOutdoorTitle()));
            }
        }

        List<CaregiverSchedule> caregiverSchedules = caregiverScheduleRepository
                .findByCaregiverIdAndIsDeleted(caregiverId, 0);
        for (CaregiverSchedule item : caregiverSchedules) {
            if (item.getEndDatetime() != null && item.getEndDatetime().isAfter(now)) {
                occupied.add(formatOccupiedSlot(
                        item.getStartDatetime(), item.getEndDatetime(), "caregiver_schedule", item.getScheduleTitle()));
            }
        }

        return occupied.stream().distinct().sorted().toList();
    }

    private static DateTimeFormatter[] buildAiTimeFormatters() {
        return new DateTimeFormatter[] {
                DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("H:mm"),
                DateTimeFormatter.ofPattern("HH:mm:ss"),
                DateTimeFormatter.ofPattern("H:mm:ss"),
                DateTimeFormatter.ISO_LOCAL_TIME
        };
    }

    /** Parses the start time returned by the AI; returns empty on failure (caller decides the fallback). */
    private static Optional<LocalTime> parseFlexibleHm(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        for (DateTimeFormatter f : AI_TIME_FORMATTERS) {
            try {
                return Optional.of(LocalTime.parse(s, f));
            } catch (Exception ignored) {
                // try next
            }
        }
        return Optional.empty();
    }

    private String formatOccupiedSlot(LocalDateTime start, LocalDateTime end, String channel, String title) {
        String startText = start == null ? "unknown" : start.format(DATETIME_FMT);
        String endText = end == null ? "unknown" : end.format(DATETIME_FMT);
        String t = title == null || title.isBlank() ? "(no title)" : safe(title);
        return channel + " | title=\"" + t + "\": " + startText + " ~ " + endText;
    }

    // Build strict prompt with dynamic business data and fixed output schema.
    private String buildPrompt(LocalDateTime now,
                               WeatherResult weatherResult,
                               List<String> mealBusyWindows,
                               List<String> medicationTimeList,
                               List<String> userOccupiedTimeList,
                               List<String> acceptHistoryList,
                               List<String> rejectHistoryList) {

        String medNote = medicationTimeList.isEmpty()
                ? "(Current list is empty: no medication plans covering today's calendar exist (including is_valid 0/1); all entries must have period=off.)"
                : "(Current list is non-empty: it contains today's scheduled times for is_valid 0/1; on/off must be calculated according to the \"Medication and Period\" section; remark must not claim there is no medication plan.)";

        return """
                You are an activity scheduling assistant for Parkinson's patient care scenarios. Follow each section in order; values in the data section are system-filled and must not be fabricated.

                [1. Output Format]
                - Output exactly one JSON object with key names matching the schema at the end; no Markdown, code fences, or explanatory text before or after.
                - Human-readable fields such as eventName and remark must be in English; remark should be approximately 25–30 English words.
                - remark must not contain specific clock times in the same format as startTime (e.g. 14:17); use descriptors such as early morning / late morning / afternoon / evening / medication on-window instead.
                - The time-of-day descriptor in remark (morning/afternoon/evening) must match the actual time period of **that entry's startTime**: for example, if startTime is between 06:00 and 11:59, do not use "evening" as the primary descriptor.
                - There must be exactly 5 eventRecommendations; durationMinutes must be between 15 and 60 inclusive.

                [2. startTime Window and Scheduling Interval]
                - Each startTime is in 24-hour format for the current day, must be later than the clock time of nowDateTime below, no earlier than 06:00 and no later than 22:59 (i.e. startTime < 23:00).
                - Use HH:mm format (e.g. 09:30); all five startTimes must be **distinct**.
                - **Time overlap is strictly forbidden**: for any two recommendations A and B where A starts before B, B.startTime must be >= A.startTime + A.durationMinutes + **60 minutes** (at least ~1 hour after the previous activity ends before the next begins). If the window is too narrow to satisfy this everywhere, a minimum gap of 45 minutes is allowed, but time segments must never overlap.
                - When feasible, the five entries should be **spread** across different parts of the day (e.g. morning/afternoon/evening; or if little time remains in the day, recommend only 2 events), avoiding clustering them all right after 06:00.
                - **Meal busy windows (mealBusyWindows)**: the data section lists a continuous **60-minute** block starting from each meal's predicted start time (both endpoints in yyyy-MM-dd HH:mm). The activity interval [startTime, startTime+durationMinutes] must not overlap any of these three meal blocks; do not treat only the meal start point as blocked while ignoring the remaining meal duration.
                - **Existing schedules (userOccupiedTimeList)**: each entry is formatted as `channel | title="...": start ~ end`; title is the name of that schedule entry (home care / outdoor / caregiver schedule). **These titles represent arrangements already on the user's calendar that have not yet ended**.
                - A new recommendation's eventName must not be **word-for-word identical** to any existing title, and must not be **substantively the same** (minor renaming, synonyms, etc.). **This constraint takes priority over** the rule in sections [5] and [6] below about copying accepted activity names verbatim from acceptHistoryList.
                - Avoid the full time spans in userOccupiedTimeList (including time overlaps); avoid the medication reminder times themselves (do not schedule an activity to start at a remind time).

                [3. Weather and Outdoor]
                - type may only be home_care or outdoor.
                - Based on the aqi, weather, and temperature values below: in bad weather, poor AQI, extreme cold, or extreme heat, do not schedule outdoor activities; use home_care instead.
                - When weather and AQI are suitable, the 5 entries should include an appropriate number of outdoor activities (provided sections [2] and [4] still allow those time slots).

                [4. Medication and Period (must strictly match system post-processing)]
                %s
                - For each medication reminder time D (HH:mm, from medicationTimeList; list includes schedules with is_valid 0 and 1, covering both completed and pending reminder times):
                  **on-window** = from D plus %d minutes (inclusive) to D plus %d minutes (exclusive) (half-open interval [D+%d min, D+%d min)).
                  If a recommendation's startTime (on the same calendar day as nowDateTime) falls within **any** on-window, that entry's period must be **on**; it must not be labelled off.
                - If medicationTimeList is empty: all 5 entries must have period **off**; remark may note that no medication reminders are scheduled today; do not fabricate medication times.
                - **If medicationTimeList is non-empty (medication scheduled today)**: in addition to the above, the following must also be satisfied:
                  (a) After now and without conflicting with section [2], **prioritise at least 3** startTimes falling within an on-window with period **on**; if fewer than 3 on-window slots remain in the day, **all** recommendations that can still fall in an on-window must have period=**on**, and the number of on entries **must not be fewer than 2**;
                  (b) Entries suitable for "on" activities (e.g. light gait exercises / stretching / brisk outdoor walks) should be **preferentially** scheduled within on-windows, with roughly 1-hour gaps before and after as required by section [2];
                  (c) remark must not contain phrases such as "no medication" or "without medication schedule" that deny the existence of the list.

                [5. Historical Feedback]
                - acceptHistoryList / rejectHistoryList are system-written string lists (including AQI/weather/temperature snapshots at the time of feedback).
                - Reusing an eventName from acceptHistoryList is **only permitted** when that name does **not** appear in any title in userOccupiedTimeList and is **not substantively the same** as any existing title. If a previously accepted name matches an occupied title (e.g. both contain "Short Park Walk"), it **must be treated as already scheduled**: do not output that same-name recommendation again; use another entry from acceptHistoryList or a completely new name.
                - Under similar weather conditions, prefer reusing previously accepted activity names **that do not conflict with existing schedules**.
                - For names appearing after eventName= in rejectHistoryList: do not output an identical name; do not output a substantively identical name with only minor renaming.

                [6. eventName]
                - **Priority (must read)**: generate a candidate name first, then check it against every title in userOccupiedTimeList: if it is **word-for-word identical** or **can be seen by the user as the same activity** (including variants with only a Short/Light/Gentle prefix or abbreviation), **discard that candidate** and choose a different name — **even if** acceptHistoryList explicitly requests that exact string to be copied verbatim.
                - If reusing a previously accepted activity that satisfies the "no schedule conflict" condition above: eventName may be word-for-word identical to the segment after "eventName=" and before " |" in some entry of acceptHistoryList.
                - In all other cases, use a new, clear English name that still does not conflict with any occupied title.
                - **Time-of-day words are strictly forbidden in eventName**: do not include morning, afternoon, evening, night, AM, PM, dawn, dusk, midday, noon, or any equivalent time-of-day descriptor as part of the activity name. The user may reschedule the activity to a different time, so a time-anchored name would become misleading. Describe the activity itself (e.g. "Park Walk", "Stretching Session", "Breathing Exercises") rather than when it happens.

                [7. Reject Exact Match]
                - Do not output a name word-for-word identical to any eventName= segment in rejectHistoryList.

                %s:
                nowDateTime: %s
                aqi: %s
                weather: %s
                temperature: %s
                mealBusyWindows (breakfast/lunch/dinner: each a continuous 60-minute block from the predicted meal start time, yyyy-MM-dd HH:mm ~ ...): %s
                medicationTimeList (today's medication remindTime for the full day, HH:mm; is_valid includes 0 and 1): %s
                userOccupiedTimeList (existing schedules: includes channel and title to avoid duplicating similar activities): %s
                acceptHistoryList (accepted feedback + environment snapshot at time of feedback, dynamic): %s
                rejectHistoryList (rejected feedback + environment snapshot at time of feedback, dynamic): %s

                Return strictly the following JSON structure (key names unchanged):
                {
                  "eventRecommendations": [
                    {
                      "eventName": "",
                      "period": "on/off",
                      "type": "home_care/outdoor",
                      "startTime": "HH:mm",
                      "durationMinutes": 30,
                      "remark": ""
                    }
                  ]
                }
                """.formatted(
                medNote,
                ON_WINDOW_START_AFTER_DOSE_MINUTES,
                ON_WINDOW_END_AFTER_DOSE_MINUTES,
                ON_WINDOW_START_AFTER_DOSE_MINUTES,
                ON_WINDOW_END_AFTER_DOSE_MINUTES,
                "Dynamic business data (each value below is automatically filled by the system; do not fabricate)",
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                weatherResult.getAqi(),
                safe(weatherResult.getWeather()),
                weatherResult.getTemperature(),
                mealBusyWindows,
                medicationTimeList,
                userOccupiedTimeList,
                acceptHistoryList,
                rejectHistoryList
        );
    }

    // Normalize AI output and enforce basic bounds; period and medication on-window are corrected by the system.
    private List<EventRecommendationItem> normalizeRecommendations(List<EventRecommendationItem> items,
                                                                   LocalDateTime now,
                                                                   List<String> medicationHmms) {
        LocalDate scheduleDay = now.toLocalDate();
        List<EventRecommendationItem> list = items.stream()
                .filter(i -> i.getEventName() != null && !i.getEventName().isBlank())
                .peek(i -> {
                    if (i.getDurationMinutes() == null) {
                        i.setDurationMinutes(30);
                    }
                    if (i.getDurationMinutes() < 15) {
                        i.setDurationMinutes(15);
                    }
                    if (i.getDurationMinutes() > 60) {
                        i.setDurationMinutes(60);
                    }
                    if (i.getType() != null) {
                        i.setType(i.getType().trim().toLowerCase(Locale.ROOT));
                    }
                    if (i.getPeriod() != null) {
                        i.setPeriod(i.getPeriod().trim().toLowerCase(Locale.ROOT));
                    }
                    if (i.getStartTime() == null || i.getStartTime().isBlank()) {
                        i.setStartTime(now.plusMinutes(10).format(TIME_FMT));
                    }
                })
                .sorted(Comparator.comparing(
                        (EventRecommendationItem i) -> parseFlexibleHm(i.getStartTime())
                                .orElse(LocalTime.MIDNIGHT)))
                .limit(5)
                .collect(Collectors.toList());

        for (int idx = 0; idx < list.size(); idx++) {
            EventRecommendationItem i = list.get(idx);
            i.setStartTime(enforceDaytimeRecommendationWindow(i.getStartTime(), now, idx));
        }
        ensureStrictlyIncreasingByStartTime(list);
        for (EventRecommendationItem i : list) {
            LocalTime st = parseFlexibleHm(i.getStartTime()).orElse(now.toLocalTime());
            i.setPeriod(inferMedicationPeriodForStart(st, scheduleDay, medicationHmms));
        }
        return list;
    }

    /**
     * If two consecutive startTimes are identical or out of order, the later one is shifted forward
     * to avoid all five entries clustering at 06:00.
     */
    private static void ensureStrictlyIncreasingByStartTime(List<EventRecommendationItem> list) {
        LocalTime prev = null;
        for (EventRecommendationItem i : list) {
            LocalTime t = parseFlexibleHm(i.getStartTime()).orElse(REC_SLOT_START_INCLUSIVE);
            t = clampToRecommendationClockWindowStatic(t);
            if (prev != null && !t.isAfter(prev)) {
                t = clampToRecommendationClockWindowStatic(prev.plusMinutes(25));
            }
            i.setStartTime(t.format(TIME_FMT));
            prev = t;
        }
    }

    private static LocalTime clampToRecommendationClockWindowStatic(LocalTime t) {
        if (t.isBefore(REC_SLOT_START_INCLUSIVE)) {
            return REC_SLOT_START_INCLUSIVE;
        }
        if (!t.isBefore(REC_SLOT_END_EXCLUSIVE)) {
            return REC_SLOT_END_EXCLUSIVE.minusMinutes(1);
        }
        return t;
    }

    /**
     * Consistent with prompt section [4]: for each medication dose time D, the on-window is
     * [D+60min, D+240min); any startTime falling within any such window is marked on.
     */
    private static String inferMedicationPeriodForStart(LocalTime startTime,
                                                        LocalDate scheduleDay,
                                                        List<String> medicationHmms) {
        if (medicationHmms == null || medicationHmms.isEmpty()) {
            return "off";
        }
        LocalDateTime rec = LocalDateTime.of(scheduleDay, startTime);
        for (String hmm : medicationHmms) {
            try {
                LocalTime doseClock = LocalTime.parse(hmm.trim(), TIME_FMT);
                LocalDateTime dose = LocalDateTime.of(scheduleDay, doseClock);
                LocalDateTime winStart = dose.plusMinutes(ON_WINDOW_START_AFTER_DOSE_MINUTES);
                LocalDateTime winEnd = dose.plusMinutes(ON_WINDOW_END_AFTER_DOSE_MINUTES);
                if (!rec.isBefore(winStart) && rec.isBefore(winEnd)) {
                    return "on";
                }
            } catch (Exception ignored) {
                // skip malformed dose time
            }
        }
        return "off";
    }

    /** Clamp HH:mm into [06:00, 23:00), and ensure interpreted start is after {@code now}. When falling back to the next day, use orderIndex to stagger slot positions. */
    private String enforceDaytimeRecommendationWindow(String startTimeStr, LocalDateTime now, int orderIndex) {
        LocalTime t = parseFlexibleHm(startTimeStr).orElse(null);
        if (t == null) {
            t = now.toLocalTime().plusMinutes(10L + orderIndex * 25L).withSecond(0).withNano(0);
        }
        t = clampToRecommendationClockWindow(t);

        LocalDate day = now.toLocalDate();
        LocalDateTime candidate = LocalDateTime.of(day, t);

        if (!candidate.isAfter(now)) {
            t = clampToRecommendationClockWindow(now.toLocalTime().plusMinutes(10)
                    .withSecond(0).withNano(0));
            candidate = LocalDateTime.of(day, t);
        }
        if (!candidate.isAfter(now)) {
            t = clampToRecommendationClockWindow(REC_SLOT_START_INCLUSIVE.plusMinutes((long) orderIndex * 25));
            candidate = LocalDateTime.of(day.plusDays(1), t);
        }

        return t.format(TIME_FMT);
    }

    private LocalTime clampToRecommendationClockWindow(LocalTime t) {
        if (t.isBefore(REC_SLOT_START_INCLUSIVE)) {
            return REC_SLOT_START_INCLUSIVE;
        }
        if (!t.isBefore(REC_SLOT_END_EXCLUSIVE)) {
            return REC_SLOT_END_EXCLUSIVE.minusMinutes(1);
        }
        return t;
    }

    private LocalDate resolveEventDateByStartTime(String startTime, LocalDate baseDate) {
        return parseFlexibleHm(startTime)
                .map(t -> t.isBefore(LocalTime.now()) ? baseDate.plusDays(1) : baseDate)
                .orElse(baseDate);
    }

    private LocalDateTime resolveStartDateTime(String startTime, LocalDate eventDate) {
        LocalTime t = parseFlexibleHm(startTime)
                .orElseThrow(() -> new RuntimeException("startTime must be a parseable clock time"));
        return LocalDateTime.of(eventDate, t);
    }

    private void saveAcceptedEventToSchedules(Long patientId,
                                              String eventName,
                                              String eventType,
                                              String remark,
                                              LocalDateTime startDateTime,
                                              LocalDateTime endDateTime,
                                              String period) {
        if ("home_care".equals(eventType)) {
            PatientHomeCareSchedule home = new PatientHomeCareSchedule();
            home.setPatientId(patientId);
            home.setHomeCareTitle(eventName);
            home.setStartDatetime(startDateTime);
            home.setEndDatetime(endDateTime);
            home.setCareNote(remark);
            home.setIsCompleted(0);
            home.setIsUrgent(0);
            home.setRecurrence("none");
            home.setIsDeleted(0);
            home.setIsPinned(0);
            home.setScheduleSource(ScheduleCascadeService.SCHEDULE_SOURCE_AI);
            home.setAiMedicationPeriod(period);
            patientHomeCareScheduleRepository.save(home);
        } else {
            PatientOutdoorSchedule outdoor = new PatientOutdoorSchedule();
            outdoor.setPatientId(patientId);
            outdoor.setOutdoorTitle(eventName);
            outdoor.setStartDatetime(startDateTime);
            outdoor.setEndDatetime(endDateTime);
            outdoor.setPrepareNote(remark);
            outdoor.setIsCompleted(0);
            outdoor.setRecurrence("none");
            outdoor.setIsDeleted(0);
            outdoor.setIsPinned(0);
            outdoor.setScheduleSource(ScheduleCascadeService.SCHEDULE_SOURCE_AI);
            outdoor.setAiMedicationPeriod(period);
            patientOutdoorScheduleRepository.save(outdoor);
        }
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    /** A human-readable snapshot of one feedback log entry, used in the prompt to describe the weather/AQI/temperature conditions under which the event was accepted or rejected. */
    private String formatHistoryLine(UserEventRecommendationLog log) {
        String name = safe(log.getEventName());
        Integer aqi = log.getAqi();
        String w = safe(log.getWeather());
        Double temp = log.getTemperature();
        LocalDate d = log.getEventDate();
        return "eventName=%s | eventDate=%s | feedbackTimeWeather: AQI=%s, weather=%s, temp=%s"
                .formatted(
                        name,
                        d == null ? "" : d.toString(),
                        aqi == null ? "unknown" : aqi.toString(),
                        w.isEmpty() ? "unknown" : w,
                        temp == null ? "unknown" : temp + "C"
                );
    }
}
