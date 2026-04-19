package com.jimuqu.agent.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.SourceKeySupport;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.message.ToolMessage;
import org.noear.solon.ai.chat.tool.ToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dashboard 会话查询服务。
 */
public class DashboardSessionService {
    private final SessionRepository sessionRepository;

    public DashboardSessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Map<String, Object> getSessions(int limit, int offset) throws Exception {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        int safeOffset = Math.max(0, offset);
        List<SessionRecord> records = sessionRepository.listRecent(safeLimit, safeOffset);
        List<Map<String, Object>> sessions = new ArrayList<Map<String, Object>>();
        for (SessionRecord record : records) {
            sessions.add(toSessionInfo(record));
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("sessions", sessions);
        result.put("total", sessionRepository.countAll());
        result.put("limit", safeLimit);
        result.put("offset", safeOffset);
        return result;
    }

    public Map<String, Object> getSessionMessages(String sessionId) throws Exception {
        SessionRecord record = sessionRepository.findById(sessionId);
        if (record == null) {
            return Collections.singletonMap("messages", Collections.emptyList());
        }

        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
        for (ChatMessage message : MessageSupport.loadMessages(record.getNdjson())) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("role", message.getRole().name().toLowerCase(Locale.ROOT));
            item.put("content", message.getContent());
            item.put("timestamp", null);

            if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                if (assistant.getToolCalls() != null && !assistant.getToolCalls().isEmpty()) {
                    List<Map<String, Object>> toolCalls = new ArrayList<Map<String, Object>>();
                    for (ToolCall call : assistant.getToolCalls()) {
                        Map<String, Object> function = new LinkedHashMap<String, Object>();
                        function.put("name", call.getName());
                        function.put("arguments", StrUtil.blankToDefault(call.getArgumentsStr(), ONode.serialize(call.getArguments())));

                        Map<String, Object> toolCall = new LinkedHashMap<String, Object>();
                        toolCall.put("id", call.getId());
                        toolCall.put("function", function);
                        toolCalls.add(toolCall);
                    }
                    item.put("tool_calls", toolCalls);
                }
            }

            if (message instanceof ToolMessage) {
                ToolMessage toolMessage = (ToolMessage) message;
                item.put("tool_name", toolMessage.getName());
                item.put("tool_call_id", toolMessage.getToolCallId());
            }

            messages.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("session_id", sessionId);
        result.put("messages", messages);
        return result;
    }

    public Map<String, Object> searchSessions(String query) throws Exception {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        if (StrUtil.isBlank(query)) {
            return Collections.singletonMap("results", results);
        }

        for (SessionRecord record : sessionRepository.search(query.trim(), 50)) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("session_id", record.getSessionId());
            item.put("snippet", buildSnippet(record, query));
            item.put("role", null);
            item.put("source", parseSource(record.getSourceKey()));
            item.put("model", record.getModelOverride());
            item.put("session_started", record.getCreatedAt());
            results.add(item);
        }

        return Collections.singletonMap("results", results);
    }

    public Map<String, Object> deleteSession(String sessionId) throws Exception {
        sessionRepository.delete(sessionId);
        return Collections.singletonMap("ok", true);
    }

    private Map<String, Object> toSessionInfo(SessionRecord record) throws Exception {
        List<ChatMessage> messages = MessageSupport.loadMessages(record.getNdjson());
        int toolCallCount = 0;
        for (ChatMessage message : messages) {
            if (message instanceof AssistantMessage) {
                AssistantMessage assistant = (AssistantMessage) message;
                if (assistant.getToolCalls() != null) {
                    toolCallCount += assistant.getToolCalls().size();
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", record.getSessionId());
        result.put("source", parseSource(record.getSourceKey()));
        result.put("model", StrUtil.blankToDefault(record.getModelOverride(), null));
        result.put("title", record.getTitle());
        result.put("started_at", record.getCreatedAt());
        result.put("ended_at", null);
        result.put("last_active", record.getUpdatedAt());
        result.put("is_active", record.getUpdatedAt() >= System.currentTimeMillis() - 5L * 60L * 1000L);
        result.put("message_count", messages.size());
        result.put("tool_call_count", toolCallCount);
        result.put("input_tokens", 0);
        result.put("output_tokens", 0);
        result.put("preview", trim(StrUtil.blankToDefault(MessageSupport.getLastUserMessage(record.getNdjson()), record.getCompressedSummary()), 160));
        return result;
    }

    private String buildSnippet(SessionRecord record, String query) throws Exception {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (ChatMessage message : MessageSupport.loadMessages(record.getNdjson())) {
            String content = StrUtil.nullToEmpty(message.getContent()).replace('\n', ' ').trim();
            if (StrUtil.isBlank(content)) {
                continue;
            }
            int index = content.toLowerCase(Locale.ROOT).indexOf(lowerQuery);
            if (index >= 0) {
                int start = Math.max(0, index - 60);
                int end = Math.min(content.length(), index + lowerQuery.length() + 60);
                String prefix = start > 0 ? "..." : "";
                String suffix = end < content.length() ? "..." : "";
                return prefix
                        + content.substring(start, index)
                        + ">>>"
                        + content.substring(index, index + lowerQuery.length())
                        + "<<<"
                        + content.substring(index + lowerQuery.length(), end)
                        + suffix;
            }
        }
        return trim(StrUtil.blankToDefault(record.getCompressedSummary(), record.getTitle()), 160);
    }

    private String parseSource(String sourceKey) {
        String[] parts = SourceKeySupport.split(sourceKey);
        if ("MEMORY".equalsIgnoreCase(parts[0])) {
            return "local";
        }
        return parts[0].toLowerCase(Locale.ROOT);
    }

    private String trim(String text, int limit) {
        String normalized = StrUtil.nullToEmpty(text).replace('\n', ' ').trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
