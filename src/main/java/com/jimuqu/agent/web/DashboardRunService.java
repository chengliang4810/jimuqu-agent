package com.jimuqu.agent.web;

import com.jimuqu.agent.core.model.AgentRunEventRecord;
import com.jimuqu.agent.core.model.AgentRunRecord;
import com.jimuqu.agent.core.repository.AgentRunRepository;
import org.noear.snack4.ONode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard Agent run 查询服务。
 */
public class DashboardRunService {
    private final AgentRunRepository agentRunRepository;

    public DashboardRunService(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    public Map<String, Object> sessionRuns(String sessionId, int limit) throws Exception {
        List<Map<String, Object>> runs = new ArrayList<Map<String, Object>>();
        for (AgentRunRecord record : agentRunRepository.listBySession(sessionId, limit <= 0 ? 20 : limit)) {
            runs.add(toRun(record));
        }
        return Collections.singletonMap("runs", runs);
    }

    public Map<String, Object> run(String runId) throws Exception {
        AgentRunRecord record = agentRunRepository.findRun(runId);
        return record == null ? Collections.<String, Object>emptyMap() : toRun(record);
    }

    public Map<String, Object> events(String runId) throws Exception {
        List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
        for (AgentRunEventRecord event : agentRunRepository.listEvents(runId)) {
            events.add(toEvent(event));
        }
        return Collections.singletonMap("events", events);
    }

    private Map<String, Object> toRun(AgentRunRecord record) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("run_id", record.getRunId());
        map.put("session_id", record.getSessionId());
        map.put("source_key", record.getSourceKey());
        map.put("agent_name", record.getAgentName());
        map.put("agent_snapshot", record.getAgentSnapshotJson() == null ? null : ONode.deserialize(record.getAgentSnapshotJson(), Object.class));
        map.put("status", record.getStatus());
        map.put("input_preview", record.getInputPreview());
        map.put("final_reply_preview", record.getFinalReplyPreview());
        map.put("provider", record.getProvider());
        map.put("model", record.getModel());
        map.put("attempts", record.getAttempts());
        map.put("input_tokens", record.getInputTokens());
        map.put("output_tokens", record.getOutputTokens());
        map.put("total_tokens", record.getTotalTokens());
        map.put("started_at", record.getStartedAt());
        map.put("finished_at", record.getFinishedAt());
        map.put("error", record.getError());
        return map;
    }

    private Map<String, Object> toEvent(AgentRunEventRecord record) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("event_id", record.getEventId());
        map.put("run_id", record.getRunId());
        map.put("session_id", record.getSessionId());
        map.put("source_key", record.getSourceKey());
        map.put("event_type", record.getEventType());
        map.put("attempt_no", record.getAttemptNo());
        map.put("provider", record.getProvider());
        map.put("model", record.getModel());
        map.put("summary", record.getSummary());
        map.put("created_at", record.getCreatedAt());
        map.put("metadata", record.getMetadataJson() == null ? null : ONode.deserialize(record.getMetadataJson(), Object.class));
        return map;
    }
}
