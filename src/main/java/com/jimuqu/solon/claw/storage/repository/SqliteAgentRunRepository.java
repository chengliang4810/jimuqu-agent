package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.RunRecoveryRecord;
import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** SQLite Agent run 仓储实现。 */
@RequiredArgsConstructor
public class SqliteAgentRunRepository implements AgentRunRepository {
    private final SqliteDatabase database;

    @Override
    public void saveRun(AgentRunRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into agent_runs (run_id, session_id, source_key, run_kind, parent_run_id, agent_name, agent_snapshot_json, status, phase, busy_policy, backgrounded, input_preview, final_reply_preview, provider, model, attempts, context_estimate_tokens, context_window_tokens, compression_count, fallback_count, tool_call_count, subtask_count, input_tokens, output_tokens, total_tokens, queued_at, started_at, heartbeat_at, last_activity_at, finished_at, exit_reason, recoverable, recovery_hint, error) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getRunId());
            statement.setString(2, record.getSessionId());
            statement.setString(3, record.getSourceKey());
            statement.setString(4, record.getRunKind());
            statement.setString(5, record.getParentRunId());
            statement.setString(6, record.getAgentName());
            statement.setString(7, record.getAgentSnapshotJson());
            statement.setString(8, record.getStatus());
            statement.setString(9, record.getPhase());
            statement.setString(10, record.getBusyPolicy());
            statement.setInt(11, record.isBackgrounded() ? 1 : 0);
            statement.setString(12, record.getInputPreview());
            statement.setString(13, record.getFinalReplyPreview());
            statement.setString(14, record.getProvider());
            statement.setString(15, record.getModel());
            statement.setInt(16, record.getAttempts());
            statement.setInt(17, record.getContextEstimateTokens());
            statement.setInt(18, record.getContextWindowTokens());
            statement.setInt(19, record.getCompressionCount());
            statement.setInt(20, record.getFallbackCount());
            statement.setInt(21, record.getToolCallCount());
            statement.setInt(22, record.getSubtaskCount());
            statement.setLong(23, record.getInputTokens());
            statement.setLong(24, record.getOutputTokens());
            statement.setLong(25, record.getTotalTokens());
            statement.setLong(26, record.getQueuedAt());
            statement.setLong(27, record.getStartedAt());
            statement.setLong(28, record.getHeartbeatAt());
            statement.setLong(29, record.getLastActivityAt());
            statement.setLong(30, record.getFinishedAt());
            statement.setString(31, record.getExitReason());
            statement.setInt(32, record.isRecoverable() ? 1 : 0);
            statement.setString(33, record.getRecoveryHint());
            statement.setString(34, record.getError());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public AgentRunRecord findRun(String runId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement("select * from agent_runs where run_id = ?");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? mapRun(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public List<AgentRunRecord> listBySession(String sessionId, int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where session_id = ? order by started_at desc limit ?");
            statement.setString(1, sessionId);
            statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public List<AgentRunRecord> listRecoverable(int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where recoverable = 1 order by last_activity_at desc limit ?");
            statement.setInt(1, Math.max(1, Math.min(limit, 200)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public List<AgentRunRecord> listActiveBefore(long beforeEpochMillis, int limit)
            throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where status in ('queued','running','waiting_approval','backgrounded','paused','interrupting') and coalesce(nullif(last_activity_at, 0), started_at) < ? order by started_at asc limit ?");
            statement.setLong(1, beforeEpochMillis);
            statement.setInt(2, Math.max(1, Math.min(limit, 200)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRun(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void markStaleRuns(long beforeEpochMillis, long now) throws Exception {
        List<AgentRunRecord> stale = listActiveBefore(beforeEpochMillis, 500);
        for (AgentRunRecord record : stale) {
            record.setStatus("recoverable");
            record.setPhase("recovery");
            record.setRecoverable(true);
            record.setRecoveryHint("服务重启或长时间无 heartbeat，已标记为可恢复。");
            record.setExitReason("stale_heartbeat");
            record.setFinishedAt(0L);
            saveRun(record);

            RunRecoveryRecord recovery = new RunRecoveryRecord();
            recovery.setRecoveryId(com.jimuqu.solon.claw.support.IdSupport.newId());
            recovery.setRunId(record.getRunId());
            recovery.setSessionId(record.getSessionId());
            recovery.setSourceKey(record.getSourceKey());
            recovery.setRecoveryType("stale_heartbeat");
            recovery.setStatus("recoverable");
            recovery.setSummary(record.getRecoveryHint());
            recovery.setCreatedAt(now);
            saveRecovery(recovery);
        }
    }

    @Override
    public void appendEvent(AgentRunEventRecord event) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events (event_id, run_id, session_id, source_key, event_type, phase, severity, attempt_no, provider, model, summary, metadata_json, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getRunId());
            statement.setString(3, event.getSessionId());
            statement.setString(4, event.getSourceKey());
            statement.setString(5, event.getEventType());
            statement.setString(6, event.getPhase());
            statement.setString(7, event.getSeverity());
            statement.setInt(8, event.getAttemptNo());
            statement.setString(9, event.getProvider());
            statement.setString(10, event.getModel());
            statement.setString(11, event.getSummary());
            statement.setString(12, event.getMetadataJson());
            statement.setLong(13, event.getCreatedAt());
            statement.executeUpdate();
            appendEventFts(connection, event);
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<AgentRunEventRecord> listEvents(String runId) throws Exception {
        List<AgentRunEventRecord> events = new ArrayList<AgentRunEventRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_run_events where run_id = ? order by created_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    events.add(mapEvent(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return events;
    }

    @Override
    public void saveToolCall(ToolCallRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into tool_calls (tool_call_id, run_id, session_id, source_key, tool_name, status, args_preview, result_preview, result_ref, error, interruptible, side_effecting, started_at, finished_at, duration_ms) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getToolCallId());
            statement.setString(2, record.getRunId());
            statement.setString(3, record.getSessionId());
            statement.setString(4, record.getSourceKey());
            statement.setString(5, record.getToolName());
            statement.setString(6, record.getStatus());
            statement.setString(7, record.getArgsPreview());
            statement.setString(8, record.getResultPreview());
            statement.setString(9, record.getResultRef());
            statement.setString(10, record.getError());
            statement.setInt(11, record.isInterruptible() ? 1 : 0);
            statement.setInt(12, record.isSideEffecting() ? 1 : 0);
            statement.setLong(13, record.getStartedAt());
            statement.setLong(14, record.getFinishedAt());
            statement.setLong(15, record.getDurationMs());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<ToolCallRecord> listToolCalls(String runId) throws Exception {
        List<ToolCallRecord> records = new ArrayList<ToolCallRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from tool_calls where run_id = ? order by started_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapToolCall(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void saveSubagentRun(SubagentRunRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into subagent_runs (subagent_id, parent_run_id, child_run_id, parent_source_key, child_source_key, session_id, name, goal_preview, status, depth, task_index, output_tail_json, error, started_at, finished_at, heartbeat_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getSubagentId());
            statement.setString(2, record.getParentRunId());
            statement.setString(3, record.getChildRunId());
            statement.setString(4, record.getParentSourceKey());
            statement.setString(5, record.getChildSourceKey());
            statement.setString(6, record.getSessionId());
            statement.setString(7, record.getName());
            statement.setString(8, record.getGoalPreview());
            statement.setString(9, record.getStatus());
            statement.setInt(10, record.getDepth());
            statement.setInt(11, record.getTaskIndex());
            statement.setString(12, record.getOutputTailJson());
            statement.setString(13, record.getError());
            statement.setLong(14, record.getStartedAt());
            statement.setLong(15, record.getFinishedAt());
            statement.setLong(16, record.getHeartbeatAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<SubagentRunRecord> listSubagents(String parentRunId) throws Exception {
        List<SubagentRunRecord> records = new ArrayList<SubagentRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from subagent_runs where parent_run_id = ? order by started_at asc");
            statement.setString(1, parentRunId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapSubagent(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void saveRecovery(RunRecoveryRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into run_recoveries (recovery_id, run_id, session_id, source_key, recovery_type, status, summary, payload_json, created_at, resolved_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getRecoveryId());
            statement.setString(2, record.getRunId());
            statement.setString(3, record.getSessionId());
            statement.setString(4, record.getSourceKey());
            statement.setString(5, record.getRecoveryType());
            statement.setString(6, record.getStatus());
            statement.setString(7, record.getSummary());
            statement.setString(8, record.getPayloadJson());
            statement.setLong(9, record.getCreatedAt());
            statement.setLong(10, record.getResolvedAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    @Override
    public List<RunRecoveryRecord> listRecoveries(String runId) throws Exception {
        List<RunRecoveryRecord> records = new ArrayList<RunRecoveryRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from run_recoveries where run_id = ? order by created_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapRecovery(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return records;
    }

    @Override
    public void pruneBefore(long beforeEpochMillis) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement deleteToolCalls =
                    connection.prepareStatement(
                            "delete from tool_calls where run_id in (select run_id from agent_runs where started_at < ?)");
            deleteToolCalls.setLong(1, beforeEpochMillis);
            deleteToolCalls.executeUpdate();
            deleteToolCalls.close();

            PreparedStatement deleteSubagents =
                    connection.prepareStatement(
                            "delete from subagent_runs where parent_run_id in (select run_id from agent_runs where started_at < ?)");
            deleteSubagents.setLong(1, beforeEpochMillis);
            deleteSubagents.executeUpdate();
            deleteSubagents.close();

            PreparedStatement deleteRecoveries =
                    connection.prepareStatement(
                            "delete from run_recoveries where run_id in (select run_id from agent_runs where started_at < ?)");
            deleteRecoveries.setLong(1, beforeEpochMillis);
            deleteRecoveries.executeUpdate();
            deleteRecoveries.close();

            PreparedStatement deleteEvents =
                    connection.prepareStatement(
                            "delete from agent_run_events where run_id in (select run_id from agent_runs where started_at < ?)");
            deleteEvents.setLong(1, beforeEpochMillis);
            deleteEvents.executeUpdate();
            deleteEvents.close();

            PreparedStatement deleteRuns =
                    connection.prepareStatement("delete from agent_runs where started_at < ?");
            deleteRuns.setLong(1, beforeEpochMillis);
            deleteRuns.executeUpdate();
            deleteRuns.close();
        } finally {
            connection.close();
        }
    }

    private AgentRunRecord mapRun(ResultSet resultSet) throws Exception {
        AgentRunRecord record = new AgentRunRecord();
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setRunKind(resultSet.getString("run_kind"));
        record.setParentRunId(resultSet.getString("parent_run_id"));
        record.setAgentName(resultSet.getString("agent_name"));
        record.setAgentSnapshotJson(resultSet.getString("agent_snapshot_json"));
        record.setStatus(resultSet.getString("status"));
        record.setPhase(resultSet.getString("phase"));
        record.setBusyPolicy(resultSet.getString("busy_policy"));
        record.setBackgrounded(resultSet.getInt("backgrounded") != 0);
        record.setInputPreview(resultSet.getString("input_preview"));
        record.setFinalReplyPreview(resultSet.getString("final_reply_preview"));
        record.setProvider(resultSet.getString("provider"));
        record.setModel(resultSet.getString("model"));
        record.setAttempts(resultSet.getInt("attempts"));
        record.setContextEstimateTokens(resultSet.getInt("context_estimate_tokens"));
        record.setContextWindowTokens(resultSet.getInt("context_window_tokens"));
        record.setCompressionCount(resultSet.getInt("compression_count"));
        record.setFallbackCount(resultSet.getInt("fallback_count"));
        record.setToolCallCount(resultSet.getInt("tool_call_count"));
        record.setSubtaskCount(resultSet.getInt("subtask_count"));
        record.setInputTokens(resultSet.getLong("input_tokens"));
        record.setOutputTokens(resultSet.getLong("output_tokens"));
        record.setTotalTokens(resultSet.getLong("total_tokens"));
        record.setQueuedAt(resultSet.getLong("queued_at"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setHeartbeatAt(resultSet.getLong("heartbeat_at"));
        record.setLastActivityAt(resultSet.getLong("last_activity_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setExitReason(resultSet.getString("exit_reason"));
        record.setRecoverable(resultSet.getInt("recoverable") != 0);
        record.setRecoveryHint(resultSet.getString("recovery_hint"));
        record.setError(resultSet.getString("error"));
        return record;
    }

    private AgentRunEventRecord mapEvent(ResultSet resultSet) throws Exception {
        AgentRunEventRecord record = new AgentRunEventRecord();
        record.setEventId(resultSet.getString("event_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setEventType(resultSet.getString("event_type"));
        record.setPhase(resultSet.getString("phase"));
        record.setSeverity(resultSet.getString("severity"));
        record.setAttemptNo(resultSet.getInt("attempt_no"));
        record.setProvider(resultSet.getString("provider"));
        record.setModel(resultSet.getString("model"));
        record.setSummary(resultSet.getString("summary"));
        record.setMetadataJson(resultSet.getString("metadata_json"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        return record;
    }

    private ToolCallRecord mapToolCall(ResultSet resultSet) throws Exception {
        ToolCallRecord record = new ToolCallRecord();
        record.setToolCallId(resultSet.getString("tool_call_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setToolName(resultSet.getString("tool_name"));
        record.setStatus(resultSet.getString("status"));
        record.setArgsPreview(resultSet.getString("args_preview"));
        record.setResultPreview(resultSet.getString("result_preview"));
        record.setResultRef(resultSet.getString("result_ref"));
        record.setError(resultSet.getString("error"));
        record.setInterruptible(resultSet.getInt("interruptible") != 0);
        record.setSideEffecting(resultSet.getInt("side_effecting") != 0);
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setDurationMs(resultSet.getLong("duration_ms"));
        return record;
    }

    private SubagentRunRecord mapSubagent(ResultSet resultSet) throws Exception {
        SubagentRunRecord record = new SubagentRunRecord();
        record.setSubagentId(resultSet.getString("subagent_id"));
        record.setParentRunId(resultSet.getString("parent_run_id"));
        record.setChildRunId(resultSet.getString("child_run_id"));
        record.setParentSourceKey(resultSet.getString("parent_source_key"));
        record.setChildSourceKey(resultSet.getString("child_source_key"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setName(resultSet.getString("name"));
        record.setGoalPreview(resultSet.getString("goal_preview"));
        record.setStatus(resultSet.getString("status"));
        record.setDepth(resultSet.getInt("depth"));
        record.setTaskIndex(resultSet.getInt("task_index"));
        record.setOutputTailJson(resultSet.getString("output_tail_json"));
        record.setError(resultSet.getString("error"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setHeartbeatAt(resultSet.getLong("heartbeat_at"));
        return record;
    }

    private RunRecoveryRecord mapRecovery(ResultSet resultSet) throws Exception {
        RunRecoveryRecord record = new RunRecoveryRecord();
        record.setRecoveryId(resultSet.getString("recovery_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setRecoveryType(resultSet.getString("recovery_type"));
        record.setStatus(resultSet.getString("status"));
        record.setSummary(resultSet.getString("summary"));
        record.setPayloadJson(resultSet.getString("payload_json"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setResolvedAt(resultSet.getLong("resolved_at"));
        return record;
    }

    private void appendEventFts(Connection connection, AgentRunEventRecord event) {
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events_fts (run_id, session_id, source_key, event_type, summary, metadata_json) values (?, ?, ?, ?, ?, ?)");
            statement.setString(1, event.getRunId());
            statement.setString(2, event.getSessionId());
            statement.setString(3, event.getSourceKey());
            statement.setString(4, event.getEventType());
            statement.setString(5, event.getSummary());
            statement.setString(6, event.getMetadataJson());
            statement.executeUpdate();
            statement.close();
        } catch (Exception ignored) {
        }
    }
}
