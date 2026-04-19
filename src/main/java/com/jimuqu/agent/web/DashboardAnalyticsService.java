package com.jimuqu.agent.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dashboard 分析服务。
 */
public class DashboardAnalyticsService {
    private final SessionRepository sessionRepository;

    public DashboardAnalyticsService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Map<String, Object> getUsage(int days) throws Exception {
        int safeDays = days <= 0 ? 30 : Math.min(days, 365);
        int totalSessions = sessionRepository.countAll();
        List<Map<String, Object>> daily = new ArrayList<Map<String, Object>>();
        List<Map<String, Object>> byModel = new ArrayList<Map<String, Object>>();
        Map<String, Object> totals = createTotals(0);

        if (totalSessions <= 0) {
            return buildResponse(daily, byModel, totals);
        }

        List<SessionRecord> records = sessionRepository.listRecent(totalSessions);
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate end = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zoneId).toLocalDate();
        LocalDate start = end.minusDays(safeDays - 1L);

        Map<String, Integer> dailySessions = new LinkedHashMap<String, Integer>();
        Map<String, Integer> modelSessions = new LinkedHashMap<String, Integer>();
        int totalInRange = 0;

        for (SessionRecord record : records) {
            long createdAt = record.getCreatedAt() > 0 ? record.getCreatedAt() : record.getUpdatedAt();
            LocalDate recordDay = Instant.ofEpochMilli(createdAt).atZone(zoneId).toLocalDate();
            if (recordDay.isBefore(start) || recordDay.isAfter(end)) {
                continue;
            }

            String dayKey = recordDay.toString();
            dailySessions.put(dayKey, dailySessions.containsKey(dayKey) ? dailySessions.get(dayKey) + 1 : 1);

            String modelKey = StrUtil.blankToDefault(record.getModelOverride(), "默认模型");
            modelSessions.put(modelKey, modelSessions.containsKey(modelKey) ? modelSessions.get(modelKey) + 1 : 1);
            totalInRange++;
        }

        if (totalInRange <= 0) {
          return buildResponse(daily, byModel, totals);
        }

        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String dayKey = cursor.toString();
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("day", dayKey);
            item.put("input_tokens", 0);
            item.put("output_tokens", 0);
            item.put("cache_read_tokens", 0);
            item.put("reasoning_tokens", 0);
            item.put("estimated_cost", 0.0D);
            item.put("actual_cost", 0.0D);
            item.put("sessions", dailySessions.containsKey(dayKey) ? dailySessions.get(dayKey) : 0);
            daily.add(item);
            cursor = cursor.plusDays(1);
        }

        for (Map.Entry<String, Integer> entry : modelSessions.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("model", normalizeModelLabel(entry.getKey()));
            item.put("input_tokens", 0);
            item.put("output_tokens", 0);
            item.put("estimated_cost", 0.0D);
            item.put("sessions", entry.getValue());
            byModel.add(item);
        }

        totals = createTotals(totalInRange);
        return buildResponse(daily, byModel, totals);
    }

    private Map<String, Object> buildResponse(List<Map<String, Object>> daily,
                                              List<Map<String, Object>> byModel,
                                              Map<String, Object> totals) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("daily", daily);
        result.put("by_model", byModel);
        result.put("totals", totals);
        return result;
    }

    private Map<String, Object> createTotals(int totalSessions) {
        Map<String, Object> totals = new LinkedHashMap<String, Object>();
        totals.put("total_input", 0);
        totals.put("total_output", 0);
        totals.put("total_cache_read", 0);
        totals.put("total_reasoning", 0);
        totals.put("total_estimated_cost", 0.0D);
        totals.put("total_actual_cost", 0.0D);
        totals.put("total_sessions", totalSessions);
        return totals;
    }

    private String normalizeModelLabel(String model) {
        String value = StrUtil.blankToDefault(model, "default").trim();
        return value.toLowerCase(Locale.ROOT);
    }
}
