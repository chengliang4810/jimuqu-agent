package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.support.SecretRedactor;
import com.jimuqu.solon.claw.support.StructuredMetadataSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** SQLite 运行事件叶子存储，负责事件主表与可降级全文索引。 */
final class SqliteRunEventStore extends SqliteRunStoreSupport {
    /**
     * 创建运行事件叶子存储。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteRunEventStore(SqliteDatabase database) {
        super(database);
    }

    /**
     * 追加运行事件。
     *
     * @param event 运行事件。
     * @throws Exception 保存失败时抛出。
     */
    void appendEvent(AgentRunEventRecord event) throws Exception {
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
            statement.setString(11, redact(event.getSummary(), 1000));
            statement.setString(12, StructuredMetadataSupport.redactJson(event.getMetadataJson()));
            statement.setLong(13, event.getCreatedAt());
            statement.executeUpdate();
            appendEventFts(connection, event);
            statement.close();
        } finally {
            connection.close();
        }
    }

    /**
     * 按运行标识列出事件。
     *
     * @param runId 运行标识。
     * @return 返回按创建时间升序排列的事件。
     * @throws Exception 查询失败时抛出。
     */
    List<AgentRunEventRecord> listEvents(String runId) throws Exception {
        List<AgentRunEventRecord> events = new ArrayList<AgentRunEventRecord>();
        Connection connection = database.openReadConnection();
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

    /**
     * 按来源、会话、运行、关键词与时间范围搜索事件。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 会话标识。
     * @param runId 运行标识。
     * @param query 关键词。
     * @param timeFrom 起始时间。
     * @param timeTo 截止时间。
     * @param limit 最大返回数量。
     * @return 返回匹配事件。
     * @throws Exception 查询失败时抛出。
     */
    List<AgentRunEventRecord> searchEvents(
            String sourceKey,
            String sessionId,
            String runId,
            String query,
            long timeFrom,
            long timeTo,
            int limit)
            throws Exception {
        List<AgentRunEventRecord> events = new ArrayList<AgentRunEventRecord>();
        Connection connection = database.openReadConnection();
        try {
            StringBuilder sql = new StringBuilder("select e.* from agent_run_events e");
            List<Object> args = new ArrayList<Object>();
            sql.append(" where 1 = 1");
            if (sourceKey != null && sourceKey.trim().length() > 0) {
                sql.append(" and e.source_key = ?");
                args.add(sourceKey);
            }
            if (sessionId != null && sessionId.trim().length() > 0) {
                sql.append(" and e.session_id = ?");
                args.add(sessionId);
            }
            if (runId != null && runId.trim().length() > 0) {
                sql.append(" and e.run_id = ?");
                args.add(runId);
            }
            if (timeFrom > 0) {
                sql.append(" and e.created_at >= ?");
                args.add(Long.valueOf(timeFrom));
            }
            if (timeTo > 0) {
                sql.append(" and e.created_at <= ?");
                args.add(Long.valueOf(timeTo));
            }
            if (query != null && query.trim().length() > 0) {
                sql.append(
                        " and (lower(coalesce(e.event_type, '')) like ?"
                                + " or lower(coalesce(e.summary, '')) like ?"
                                + " or lower(coalesce(e.metadata_json, '')) like ?)");
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }
            sql.append(" order by e.created_at desc limit ?");
            args.add(Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200)));
            PreparedStatement statement = connection.prepareStatement(sql.toString());
            bindArgs(statement, args);
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

    /**
     * 映射运行事件记录。
     *
     * @param resultSet 查询结果。
     * @return 返回运行事件记录。
     * @throws Exception 映射失败时抛出。
     */
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

    /**
     * 最佳努力追加运行事件全文索引。
     *
     * @param connection 当前写连接。
     * @param event 运行事件。
     */
    private void appendEventFts(Connection connection, AgentRunEventRecord event) {
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events_fts (run_id, session_id, source_key, event_type, summary, metadata_json) values (?, ?, ?, ?, ?, ?)");
            statement.setString(1, event.getRunId());
            statement.setString(2, event.getSessionId());
            statement.setString(3, event.getSourceKey());
            statement.setString(4, event.getEventType());
            statement.setString(5, SecretRedactor.redact(event.getSummary(), 1000));
            statement.setString(6, StructuredMetadataSupport.redactJson(event.getMetadataJson()));
            statement.executeUpdate();
            statement.close();
        } catch (Exception e) {
            logBestEffortFailure("agent_run_events_fts_append", e);
        }
    }
}
