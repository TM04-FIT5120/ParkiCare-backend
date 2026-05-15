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
    /** 兼容模型返回的 6:00、09:15:00 等；严格 HH:mm 解析失败时不再整机默认成同一 06:00。 */
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

        String breakfastTime = mealScheduleService.predictMealTime(request.getCaregiverId(), "BREAKFAST");
        String lunchTime = mealScheduleService.predictMealTime(request.getCaregiverId(), "LUNCH");
        String dinnerTime = mealScheduleService.predictMealTime(request.getCaregiverId(), "DINNER");

        List<String> mealBusyWindows = buildMealBusyWindows(now.toLocalDate(), breakfastTime, lunchTime, dinnerTime);

        List<String> medicationTimeList = buildMedicationTimeList(patientId, now.toLocalDate());
        List<String> userOccupiedTimeList = buildUserOccupiedTimeList(
                request.getCaregiverId(), patientId, now);

        LocalDate fromDate = now.toLocalDate().minusDays(14);
        // 各 5 条：accept 取 score 最高；reject 取 score 最低；同分按 id 新在前。每条含反馈当时天气快照。
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

    /**
     * 当日「全天」用药提醒时刻（HH:mm），与 {@link MedicationPlanService#getTodayAllPlansByPatient} 一致；
     * is_valid 0/1 均纳入（含已完成与未完成排程），不按当前时刻截断。
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
     * 三餐：每条为「预测开餐 HH:mm」起固定 {@link #MEAL_BUSY_BLOCK_MINUTES} 分钟的占用区间（供 prompt 与避让）。
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

    /** 解析 AI 返回的开始时刻；失败时返回 empty（由调用方决定兜底）。 */
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
                ? "（当前为空列表：当日无覆盖日历的用药排程（含 is_valid 0/1）；所有条目的 period 须为 off。）"
                : "（当前非空：列表含 is_valid 0/1 的当日排程时刻；必须按本章「用药与 period」计算 on/off，禁止在 remark 中声称无用药计划。）";

        return """
                你是帕金森患者照护场景的「活动安排」生成助手。按章节执行；数据区数值以系统填充为准，不得臆造。

                【1. 输出形态】
                - 只输出一个 JSON 对象，键名与文末 schema 完全一致；禁止 Markdown、代码围栏、前后解释文字。
                - eventName、remark 等可读文本使用英文；remark 约 25～30 个英文单词。
                - remark 中禁止出现与 startTime 同格式的具体钟点（如 14:17）；可用 early morning / late morning / afternoon / evening、medication on-window 等描述时段。
                - remark 所描述的时段（morning/afternoon/evening）必须与 **该条 startTime** 在一天中的真实时段一致：例如 startTime 在 06:00–11:59 则不可用 "evening" 作为主描述。
                - 共 5 条 eventRecommendations；durationMinutes 在 15～60（含边界）。

                【2. startTime 时间窗与排程间隔】
                - 每条 startTime 为当天 24 小时制，须晚于下方 nowDateTime 的时钟时刻，且不早于 06:00、且不晚于 22:59（即开始时刻 < 23:00）。
                - 建议统一为 HH:mm（如 09:30）；五条 startTime 必须**互不相同**。
                - **严禁时段重合**：对任意两条推荐 A、B，若 A 的开始时间早于 B，则必须满足：B.startTime ≥ A.startTime + A.durationMinutes + **60 分钟**（即上一条活动结束后至少隔约 1 小时再开始下一条）。若因窗口过窄无法处处满足，允许最小间隔 45 分钟，但仍不得时间段重叠。
                - 五条在可行时应**分散**在当天不同时段（如上午/下午/傍晚,或当天剩余不多就只推荐2条event），避免全部挤在 06:00 后极短时间内。
                - **三餐占用（mealBusyWindows）**：数据区列出每餐「预测开餐时刻」起连续 **60 分钟**的**整段**时间（起止均为 yyyy-MM-dd HH:mm）。推荐活动的 [startTime, startTime+durationMinutes] 不得与该三餐任一段重叠；也不得仅压在「开餐点」而忽略后续用餐时长。
                - **已有日程（userOccupiedTimeList）**：每条格式为 `渠道 | title="…": 开始 ~ 结束`；title 为该条日程的标题（居家/户外/照护者日程）。**这些 title 表示用户日历上已经存在、尚未结束的安排**。
                - 新推荐的 eventName：禁止与任一已有 title **逐字相同**；禁止 **实质同类**（轻微改名、同义等）。**此约束优先于** 下文章节【5】【6】中「从 acceptHistoryList 逐字拷贝曾接受活动名」的规则。
                - 避开 userOccupiedTimeList 中的整段区间（含时间重叠）；避开用药提醒点本身（不要在 remind 时刻安排活动开始）。

                【3. 天气与 outdoor】
                - type 只能是 home_care 或 outdoor。
                - 结合下方 aqi、weather、temperature：恶劣天气、AQI 差、过冷或过热时不得安排 outdoor，应安排 home_care。
                - 天气与 AQI 适宜时，5 条中应包含适量 outdoor（若规则 2、4 仍允许该时段）。

                【4. 用药与 period（与系统后处理一致，必须严格遵守）】
                %s
                - 对每个用药提醒时刻 D（HH:mm，来自 medicationTimeList；列表含 is_valid 为 0 与 1 的排程，含已完成与未完成对应的提醒时刻）：
                  **on 窗口** = 从 D 起满 %d 分钟之后，到满 %d 分钟之前（左闭右开，即 [D+%d min, D+%d min)）。
                  若某条推荐的 startTime（与 nowDateTime 同一日历日）落在**任意一个** on 窗口内，则该条 period 必须为 **on**；不得标为 off。
                - 若 medicationTimeList 为空：全部 5 条 period 必须为 **off**，remark 可说明当日无排程用药提醒；不得虚构用药时刻。
                - **若 medicationTimeList 非空（当日有服药排程）**：除上述规则外还须同时满足：
                  (a) 在 now 之后、且与规则【2】不冲突的前提下，**优先保证至少 3 条** startTime 落在某个 on 窗口内且 period 均为 **on**；若当日剩余可排 on 窗口客观上不足 3 个，则**所有**仍能落入 on 窗口的推荐必须 period=**on**，且 on 条数**不得少于 2**；
                  (b) 适合作为「开期」活动的条目（如轻度步态/伸展/户外快走等）应**优先**排进 on 窗口，并按【2】与前后活动留出约 1 小时间隔；
                  (c) remark 不得写「no medication」「without medication schedule」等否认列表的措辞。

                【5. 历史反馈】
                - acceptHistoryList / rejectHistoryList 为系统写入的字符串列表（含反馈当时的 AQI/天气/温度快照）。
                - 「从 acceptHistoryList 复用 eventName」**仅允许**在：该名称**未**出现在 userOccupiedTimeList 任一条的 title 中、且与已有 title **不构成实质同类** 时。若曾接受名与某 occupied title 相同（如都曾出现 "Short Park Walk"），**必须视为日程已占用**：不得再输出该同名推荐，应改用 acceptHistoryList 中其它条目或全新名称。
                - 在相近天气条件下，优先复用**未与已有日程冲突**的曾接受活动名。
                - rejectHistoryList 中出现的 eventName= 后的名称：禁止输出完全同名；禁止实质相同仅轻微改名。

                【6. eventName】
                - **优先级（必读）**：先生成候选名，再对照 userOccupiedTimeList 每一条 title：凡 **逐字相同** 或 **用户可视为同一活动**（含仅加 Short/Light/Gentle 等前缀、缩写变体），**一律废弃该候选**，改选其它名称——**即使** acceptHistoryList 明确要求对该字符串逐字拷贝也不例外。
                - 若复用曾接受活动且满足上一条的「无日程冲突」：eventName 可与 acceptHistoryList 某条里 "eventName=" 之后、到 " |" 之前的片段逐字一致。
                - 其余情况使用新的清晰英文名称，且仍不得与任一 occupied title 冲突。

                【7. reject 精确匹配】
                - 禁止与 rejectHistoryList 中任一 eventName= 段逐字相同。

                %s：
                nowDateTime: %s
                aqi: %s
                weather: %s
                temperature: %s
                mealBusyWindows（早餐/午餐/晚餐：自预测开餐时刻起各连续 60 分钟，yyyy-MM-dd HH:mm ~ …）: %s
                medicationTimeList（当日「全天」用药 remindTime，HH:mm；is_valid 含 0/1）: %s
                userOccupiedTimeList（已有日程：含渠道与 title，便于避免与同类活动重复）: %s
                acceptHistoryList（接受反馈+当时环境快照，动态）: %s
                rejectHistoryList（拒绝反馈+当时环境快照，动态）: %s

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
                medNote,
                ON_WINDOW_START_AFTER_DOSE_MINUTES,
                ON_WINDOW_END_AFTER_DOSE_MINUTES,
                ON_WINDOW_START_AFTER_DOSE_MINUTES,
                ON_WINDOW_END_AFTER_DOSE_MINUTES,
                "动态业务数据（以下每项值由系统自动填充，请勿臆造）",
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

    // Normalize AI output and enforce basic bounds; period 与用药 on 窗口由系统校正。
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
     * 若连续两条 startTime 相同或逆序，后一条顺延，避免五条都压在 06:00。
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
     * 与 prompt 【4】一致：对每个服药时刻 D，on 窗口为 [D+60min, D+240min)；startTime 落在任一窗口内则 on。
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

    /** Clamp HH:mm into [06:00, 23:00), and ensure interpreted start is after {@code now}. 次日回退时用 orderIndex 错开槽位。 */
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

    /** 一条反馈日志的可读快照，供 prompt 说明「在何种天气/AQI/温度下」接受或拒绝。 */
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
