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
import com.caregiver.repository.MedicationReminderRepository;
import com.caregiver.repository.PatientHomeCareScheduleRepository;
import com.caregiver.repository.PatientOutdoorScheduleRepository;
import com.caregiver.repository.PatientRepository;
import com.caregiver.repository.UserEventRecommendationLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventRecommendationService {

    private final PatientRepository patientRepository;
    private final MedicationReminderRepository medicationReminderRepository;
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
    /** Allowed clock window for recommendation start times (aligned with caregiver daytime care). */
    private static final LocalTime REC_SLOT_START_INCLUSIVE = LocalTime.of(6, 0);
    private static final LocalTime REC_SLOT_END_EXCLUSIVE = LocalTime.of(23, 0);

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

        String breakfastTime = mealScheduleService.predictMealTime(request.getCaregiverId(), "BREAKFAST");
        String lunchTime = mealScheduleService.predictMealTime(request.getCaregiverId(), "LUNCH");
        String dinnerTime = mealScheduleService.predictMealTime(request.getCaregiverId(), "DINNER");

        List<String> medicationTimeList = buildMedicationTimeList(patientId, now.toLocalDate());
        List<String> userOccupiedTimeList = buildUserOccupiedTimeList(
                request.getCaregiverId(), patientId, now);

        LocalDate fromDate = now.toLocalDate().minusDays(14);
        List<String> acceptHistoryList = userEventRecommendationLogRepository
                .findByCaregiverIdAndUserFeedbackAndEventDateGreaterThanEqualOrderByIdDesc(
                        request.getCaregiverId(), "accept", fromDate)
                .stream()
                .map(UserEventRecommendationLog::getEventName)
                .distinct()
                .toList();

        List<String> rejectHistoryList = userEventRecommendationLogRepository
                .findByCaregiverIdAndUserFeedbackAndEventDateGreaterThanEqualOrderByIdDesc(
                        request.getCaregiverId(), "reject", fromDate)
                .stream()
                .map(UserEventRecommendationLog::getEventName)
                .distinct()
                .toList();

        String prompt = buildPrompt(
                now,
                weatherResult,
                breakfastTime,
                lunchTime,
                dinnerTime,
                medicationTimeList,
                userOccupiedTimeList,
                acceptHistoryList,
                rejectHistoryList
        );

        try {
            String aiJson = qwenChatClient.generateJsonByPrompt(prompt);
            EventRecommendationResponse response =
                    objectMapper.readValue(aiJson, EventRecommendationResponse.class);

            if (response.getEventRecommendations() == null || response.getEventRecommendations().isEmpty()) {
                throw new RuntimeException("AI returned empty recommendations");
            }

            List<EventRecommendationItem> normalized = normalizeRecommendations(response.getEventRecommendations(), now);
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
                    request.getCaregiverId(),
                    patientId,
                    safe(request.getEventName()),
                    eventType,
                    safe(request.getRemark()),
                    startDateTime,
                    endDateTime
            );
        }
        return savedLog;
    }

    // Collect today's valid medication times in HH:mm.
    private List<String> buildMedicationTimeList(Long patientId, LocalDate today) {
        List<MedicationPlan> plans = medicationReminderRepository.findByPatientIdAndIsValid(patientId, 1);
        return plans.stream()
                .filter(p -> p.getStartDate() != null && !p.getStartDate().isAfter(today))
                .filter(p -> p.getEndDate() == null || !p.getEndDate().isBefore(today))
                .map(MedicationPlan::getRemindTime)
                .filter(t -> t != null)
                .distinct()
                .sorted()
                .map(t -> t.format(TIME_FMT))
                .toList();
    }

    // Merge all occupied schedule ranges to prevent overlap in prompt.
    private List<String> buildUserOccupiedTimeList(Long caregiverId, Long patientId, LocalDateTime now) {
        List<String> occupied = new ArrayList<>();

        List<PatientHomeCareSchedule> homeCares = patientHomeCareScheduleRepository
                .findByPatientIdAndIsDeleted(patientId, 0);
        for (PatientHomeCareSchedule item : homeCares) {
            if (item.getEndDatetime() != null && item.getEndDatetime().isAfter(now)) {
                occupied.add(formatRange(item.getStartDatetime(), item.getEndDatetime(), "home_care"));
            }
        }

        List<PatientOutdoorSchedule> outdoors = patientOutdoorScheduleRepository
                .findByPatientIdAndIsDeleted(patientId, 0);
        for (PatientOutdoorSchedule item : outdoors) {
            if (item.getEndDatetime() != null && item.getEndDatetime().isAfter(now)) {
                occupied.add(formatRange(item.getStartDatetime(), item.getEndDatetime(), "outdoor"));
            }
        }

        List<CaregiverSchedule> caregiverSchedules = caregiverScheduleRepository
                .findByCaregiverIdAndIsDeleted(caregiverId, 0);
        for (CaregiverSchedule item : caregiverSchedules) {
            if (item.getEndDatetime() != null && item.getEndDatetime().isAfter(now)) {
                occupied.add(formatRange(item.getStartDatetime(), item.getEndDatetime(), "caregiver_schedule"));
            }
        }

        return occupied.stream().distinct().sorted().toList();
    }

    private String formatRange(LocalDateTime start, LocalDateTime end, String type) {
        String startText = start == null ? "unknown" : start.format(DATETIME_FMT);
        String endText = end == null ? "unknown" : end.format(DATETIME_FMT);
        return type + ": " + startText + " ~ " + endText;
    }

    // Build strict prompt with dynamic business data and fixed output schema.
    private String buildPrompt(LocalDateTime now,
                               WeatherResult weatherResult,
                               String breakfastTime,
                               String lunchTime,
                               String dinnerTime,
                               List<String> medicationTimeList,
                               List<String> userOccupiedTimeList,
                               List<String> acceptHistoryList,
                               List<String> rejectHistoryList) {

        return """
                你是帕金森患者照护场景的「活动安排」生成助手。
                必须严格遵守以下全部规则：

                1) 活动类型只能是两类：home_care（居家照护）、outdoor（户外事件）。
                2) 推荐条目总数必须为 5 条（两类合计）。
                3) 必须结合实时 AQI、天气、气温判断是否可户外：
                   - AQI 差、雨天、过冷或过热：禁止推荐 outdoor。
                   - 仅当天气适宜、AQI 优良且温度适中时，才允许推荐 outdoor。
                4) 严格避开固定三餐时间、用药计划时间、用户已占用的全部活动时段；
                   时间段不得重叠；不得在同一时段重复同类活动。
                5) startTime（HH:mm）必须同时满足：①不早于当前系统时间；②时刻落在早上 06:00（含）至晚上 23:00（不含）之间，
                   不得在凌晨 00:00～06:00 或 23:00 及之后开始。
                6) 根据 medicationTimeList 自行推断 period：
                   - on period：服药后 1～2 小时（可户外或轻度康复）；
                   - off period：其余时间（优先 home_care）。
                7) 参考用户历史偏好：
                   - 优先沿用用户曾经接受的活动变体；
                   - 规避用户拒绝过的活动内容。
                7b) rejectHistoryList 为数据库中精确的、曾被拒绝的 eventName 字符串。
                   - 禁止输出与 rejectHistoryList 中任一字符串完全一致的 eventName。
                   - 若活动实质与被拒活动相同，禁止仅做轻微改名的等价推荐。
                8) 每条推荐必须包含固定字段：
                   eventName、period、type、startTime、durationMinutes、remark。
                   remark 约 30 个英文单词。
                   若推荐的仍是用户曾接受过的同类活动，eventName 必须从 acceptHistoryList（数据库 canonical 字符串）逐字拷贝，
                   不得改写、不改标点、不缩写；
                   若为 acceptHistoryList 中不存在的全新活动，eventName 使用新的清晰英文名称。
                9) startTime 必须为 24 小时制 HH:mm，且与规则 5 的时间窗口完全一致（早于 23:00、不早于 06:00，且不早于当前时刻）。
                10) durationMinutes 必须在 15～60（含边界）之间。
                11) JSON 内的自然语言文案（eventName、remark 等可读文本）必须使用英文。
                12) 只输出纯粹 JSON：禁止 Markdown、禁止代码围栏、禁止多余解释。

                %s：
                nowDateTime: %s
                aqi: %s
                weather: %s
                temperature: %s
                breakfastTime: %s
                lunchTime: %s
                dinnerTime: %s
                medicationTimeList: %s
                userOccupiedTimeList: %s
                acceptHistoryList: %s
                rejectHistoryList: %s

                必须严格按下述 JSON 结构返回（键名保持不变）：
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
                "动态业务数据（以下每项值由系统自动填充，请勿臆造）",
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                weatherResult.getAqi(),
                safe(weatherResult.getWeather()),
                weatherResult.getTemperature(),
                breakfastTime,
                lunchTime,
                dinnerTime,
                medicationTimeList,
                userOccupiedTimeList,
                acceptHistoryList,
                rejectHistoryList
        );
    }

    // Normalize AI output and enforce basic bounds.
    private List<EventRecommendationItem> normalizeRecommendations(List<EventRecommendationItem> items, LocalDateTime now) {
        return items.stream()
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
                    i.setStartTime(enforceDaytimeRecommendationWindow(i.getStartTime(), now));
                })
                .sorted(Comparator.comparing(EventRecommendationItem::getStartTime))
                .limit(5)
                .collect(Collectors.toList());
    }

    /** Clamp HH:mm into [06:00, 23:00), and ensure interpreted start is after {@code now} (same day then next day morning). */
    private String enforceDaytimeRecommendationWindow(String startTimeStr, LocalDateTime now) {
        LocalTime t;
        try {
            t = LocalTime.parse(startTimeStr.trim(), TIME_FMT);
        } catch (Exception e) {
            t = REC_SLOT_START_INCLUSIVE;
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
            t = REC_SLOT_START_INCLUSIVE;
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
        try {
            LocalTime t = LocalTime.parse(startTime, TIME_FMT);
            return t.isBefore(LocalTime.now()) ? baseDate.plusDays(1) : baseDate;
        } catch (Exception e) {
            return baseDate;
        }
    }

    private LocalDateTime resolveStartDateTime(String startTime, LocalDate eventDate) {
        try {
            LocalTime t = LocalTime.parse(startTime, TIME_FMT);
            return LocalDateTime.of(eventDate, t);
        } catch (Exception e) {
            throw new RuntimeException("startTime must be HH:mm");
        }
    }

    private void saveAcceptedEventToSchedules(Long caregiverId,
                                              Long patientId,
                                              String eventName,
                                              String eventType,
                                              String remark,
                                              LocalDateTime startDateTime,
                                              LocalDateTime endDateTime) {
        CaregiverSchedule caregiverSchedule = new CaregiverSchedule();
        caregiverSchedule.setCaregiverId(caregiverId);
        caregiverSchedule.setScheduleTitle(eventName);
        caregiverSchedule.setStartDatetime(startDateTime);
        caregiverSchedule.setEndDatetime(endDateTime);
        caregiverSchedule.setScheduleNote(remark);
        caregiverSchedule.setRecurrence("none");
        caregiverSchedule.setIsCompleted(0);
        caregiverSchedule.setIsConflict(0);
        caregiverSchedule.setIsDeleted(0);
        caregiverScheduleRepository.save(caregiverSchedule);

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
            patientOutdoorScheduleRepository.save(outdoor);
        }
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }
}
