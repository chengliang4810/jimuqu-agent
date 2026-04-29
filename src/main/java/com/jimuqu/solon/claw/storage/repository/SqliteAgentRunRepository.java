package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
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
                            "insert or replace into agent_runs (run_id, session_id, source_key, agent_name, agent_snapshot_json, status, input_preview, final_reply_preview, provider, model, attempts, input_tokens, output_tokens, total_tokens, started_at, finished_at, error) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getRunId());
            statement.setString(2, record.getSessionId());
            statement.setString(3, record.getSourceKey());
            statement.setString(4, record.getAgentName());
            statement.setString(5, record.getAgentSnapshotJson());
            statement.setString(6, record.getStatus());
            statement.setString(7, record.getInputPreview());
            statement.setString(8, record.getFinalReplyPreview());
            statement.setString(9, record.getProvider());
            statement.setString(10, record.getModel());
            statement.setInt(11, record.getAttempts());
            statement.setLong(12, record.getInputTokens());
            statement.setLong(13, record.getOutputTokens());
            statement.setLong(14, record.getTotalTokens());
            statement.setLong(15, record.getStartedAt());
            statement.setLong(16, record.getFinishedAt());
            statement.setString(17, record.getError());
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
    public void appendEvent(AgentRunEventRecord event) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events (event_id, run_id, session_id, source_key, event_type, attempt_no, provider, model, summary, metadata_json, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getRunId());
            statement.setString(3, event.getSessionId());
            statement.setString(4, event.getSourceKey());
            statement.setString(5, event.getEventType());
            statement.setInt(6, event.getAttemptNo());
            statement.setString(7, event.getProvider());
            statement.setString(8, event.getModel());
            statement.setString(9, event.getSummary());
            statement.setString(10, event.getMetadataJson());
            statement.setLong(11, event.getCreatedAt());
            statement.executeUpdate();
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
    public void pruneBefore(long beforeEpochMillis) throws Exception {
        Connection connection = database.openConnection();
        try {
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
        record.setAgentName(resultSet.getString("agent_name"));
        record.setAgentSnapshotJson(resultSet.getString("agent_snapshot_json"));
        record.setStatus(resultSet.getString("status"));
        record.setInputPreview(resultSet.getString("input_preview"));
        record.setFinalReplyPreview(resultSet.getString("final_reply_preview"));
        record.setProvider(resultSet.getString("provider"));
        record.setModel(resultSet.getString("model"));
        record.setAttempts(resultSet.getInt("attempts"));
        record.setInputTokens(resultSet.getLong("input_tokens"));
        record.setOutputTokens(resultSet.getLong("output_tokens"));
        record.setTotalTokens(resultSet.getLong("total_tokens"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
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
        record.setAttemptNo(resultSet.getInt("attempt_no"));
        record.setProvider(resultSet.getString("provider"));
        record.setModel(resultSet.getString("model"));
        record.setSummary(resultSet.getString("summary"));
        record.setMetadataJson(resultSet.getString("metadata_json"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        return record;
    }
}
