package com.jimuqu.agent.core.model;

import com.jimuqu.agent.core.repository.AgentRunRepository;
import com.jimuqu.agent.support.IdSupport;
import com.jimuqu.agent.support.SecretRedactor;
import org.noear.snack4.ONode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前 Agent run 的追踪上下文。
 */
public class AgentRunContext {
    private final AgentRunRepository repository;
    private final String runId;
    private final String sessionId;
    private final String sourceKey;
    private int attemptNo;
    private String provider;
    private String model;

    public AgentRunContext(AgentRunRepository repository, String runId, String sessionId, String sourceKey) {
        this.repository = repository;
        this.runId = runId;
        this.sessionId = sessionId;
        this.sourceKey = sourceKey;
    }

    public String getRunId() {
        return runId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public void setAttempt(int attemptNo, String provider, String model) {
        this.attemptNo = attemptNo;
        this.provider = provider;
        this.model = model;
    }

    public void event(String eventType, String summary) {
        event(eventType, summary, null);
    }

    public void event(String eventType, String summary, Map<String, Object> metadata) {
        if (repository == null) {
            return;
        }
        try {
            AgentRunEventRecord event = new AgentRunEventRecord();
            event.setEventId(IdSupport.newId());
            event.setRunId(runId);
            event.setSessionId(sessionId);
            event.setSourceKey(sourceKey);
            event.setEventType(eventType);
            event.setAttemptNo(attemptNo);
            event.setProvider(provider);
            event.setModel(model);
            event.setSummary(safe(summary, 1000));
            event.setMetadataJson(metadata == null ? null : safe(ONode.serialize(metadata), 4000));
            event.setCreatedAt(System.currentTimeMillis());
            repository.appendEvent(event);
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> metadata(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(key, value);
        return map;
    }

    public static String safe(String text, int limit) {
        String redacted = SecretRedactor.redact(text, limit);
        if (redacted == null) {
            return null;
        }
        return redacted.length() <= limit ? redacted : redacted.substring(0, limit) + "...";
    }
}
