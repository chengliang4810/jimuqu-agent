package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** SQLite 工具调用存储，保持主记录、运行计数与可降级全文索引的既有事务语义。 */
final class SqliteToolCallStore extends SqliteRunStoreSupport {
    /**
     * 创建工具调用存储。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteToolCallStore(SqliteDatabase database) {
        super(database);
    }

    /**
     * 保存工具调用，并在同一事务内维护运行终态调用计数。
     *
     * @param record 工具调用记录。
     * @throws Exception 保存失败时抛出。
     */
    void save(ToolCallRecord record) throws Exception {
        Connection connection = database.openConnection();
        boolean transactionOwner = connection.getAutoCommit();
        try {
            if (transactionOwner) {
                connection.setAutoCommit(false);
            }
            String previousRunId = null;
            String previousStatus = null;
            PreparedStatement previousStatement =
                    connection.prepareStatement(
                            "select run_id, status from tool_calls where tool_call_id = ?");
            previousStatement.setString(1, record.getToolCallId());
            ResultSet previousResultSet = previousStatement.executeQuery();
            try {
                if (previousResultSet.next()) {
                    previousRunId = previousResultSet.getString("run_id");
                    previousStatus = previousResultSet.getString("status");
                }
            } finally {
                previousResultSet.close();
                previousStatement.close();
            }
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into tool_calls (tool_call_id, run_id, session_id, source_key, tool_name, status, args_preview, result_preview, result_ref, error, read_only, interruptible, side_effecting, result_indexable, output_limit_bytes, result_size_bytes, execution_policy, started_at, finished_at, duration_ms) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                    + "on conflict(tool_call_id) do update set "
                                    + "run_id=excluded.run_id, session_id=excluded.session_id, source_key=excluded.source_key, tool_name=excluded.tool_name, "
                                    + "status=excluded.status, args_preview=excluded.args_preview, result_preview=excluded.result_preview, result_ref=excluded.result_ref, error=excluded.error, "
                                    + "read_only=excluded.read_only, interruptible=excluded.interruptible, side_effecting=excluded.side_effecting, result_indexable=excluded.result_indexable, "
                                    + "output_limit_bytes=excluded.output_limit_bytes, result_size_bytes=excluded.result_size_bytes, execution_policy=excluded.execution_policy, "
                                    + "started_at=excluded.started_at, finished_at=excluded.finished_at, duration_ms=excluded.duration_ms");
            statement.setString(1, record.getToolCallId());
            statement.setString(2, record.getRunId());
            statement.setString(3, record.getSessionId());
            statement.setString(4, record.getSourceKey());
            statement.setString(5, record.getToolName());
            statement.setString(6, record.getStatus());
            statement.setString(7, redact(record.getArgsPreview(), 8000));
            statement.setString(8, redact(record.getResultPreview(), 8000));
            statement.setString(9, redact(record.getResultRef(), 1000));
            statement.setString(10, redact(record.getError(), 2000));
            statement.setInt(11, record.isReadOnly() ? 1 : 0);
            statement.setInt(12, record.isInterruptible() ? 1 : 0);
            statement.setInt(13, record.isSideEffecting() ? 1 : 0);
            statement.setInt(14, record.isResultIndexable() ? 1 : 0);
            statement.setInt(15, record.getOutputLimitBytes());
            statement.setLong(16, record.getResultSizeBytes());
            statement.setString(17, record.getExecutionPolicy());
            statement.setLong(18, record.getStartedAt());
            statement.setLong(19, record.getFinishedAt());
            statement.setLong(20, record.getDurationMs());
            statement.executeUpdate();
            statement.close();
            updateToolCallCount(
                    connection,
                    previousRunId,
                    previousStatus,
                    record.getRunId(),
                    record.getStatus());
            appendToolResultFts(connection, record);
            if (transactionOwner) {
                connection.commit();
            }
        } catch (Exception e) {
            if (transactionOwner) {
                try {
                    connection.rollback();
                } catch (Exception rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            throw e;
        } finally {
            if (transactionOwner) {
                try {
                    connection.setAutoCommit(true);
                } catch (Exception e) {
                    logBestEffortFailure("tool_call_transaction_reset", e);
                }
            }
            connection.close();
        }
    }

    /**
     * 按运行标识列出工具调用。
     *
     * @param runId 运行标识。
     * @return 返回按开始时间升序排列的工具调用。
     * @throws Exception 查询失败时抛出。
     */
    List<ToolCallRecord> list(String runId) throws Exception {
        List<ToolCallRecord> records = new ArrayList<ToolCallRecord>();
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from tool_calls where run_id = ? order by started_at asc");
            statement.setString(1, runId);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(map(resultSet));
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

    /**
     * 按来源、会话、运行、工具、关键词与时间范围搜索工具调用。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 会话标识。
     * @param runId 运行标识。
     * @param toolName 工具名称。
     * @param query 关键词。
     * @param timeFrom 起始时间。
     * @param timeTo 截止时间。
     * @param limit 最大返回数量。
     * @return 返回匹配工具调用。
     * @throws Exception 查询失败时抛出。
     */
    List<ToolCallRecord> search(
            String sourceKey,
            String sessionId,
            String runId,
            String toolName,
            String query,
            long timeFrom,
            long timeTo,
            int limit)
            throws Exception {
        List<ToolCallRecord> records = new ArrayList<ToolCallRecord>();
        Connection connection = database.openReadConnection();
        try {
            StringBuilder sql = new StringBuilder("select * from tool_calls where 1 = 1");
            List<Object> args = new ArrayList<Object>();
            if (sourceKey != null && sourceKey.trim().length() > 0) {
                sql.append(" and source_key = ?");
                args.add(sourceKey);
            }
            if (sessionId != null && sessionId.trim().length() > 0) {
                sql.append(" and session_id = ?");
                args.add(sessionId);
            }
            if (runId != null && runId.trim().length() > 0) {
                sql.append(" and run_id = ?");
                args.add(runId);
            }
            if (toolName != null && toolName.trim().length() > 0) {
                sql.append(" and tool_name = ?");
                args.add(toolName);
            }
            if (timeFrom > 0) {
                sql.append(" and started_at >= ?");
                args.add(Long.valueOf(timeFrom));
            }
            if (timeTo > 0) {
                sql.append(" and started_at <= ?");
                args.add(Long.valueOf(timeTo));
            }
            if (query != null && query.trim().length() > 0) {
                sql.append(
                        " and (lower(coalesce(tool_name, '')) like ?"
                                + " or lower(coalesce(args_preview, '')) like ?"
                                + " or lower(coalesce(result_preview, '')) like ?"
                                + " or lower(coalesce(error, '')) like ?)");
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }
            sql.append(" order by started_at desc limit ?");
            args.add(Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200)));
            PreparedStatement statement = connection.prepareStatement(sql.toString());
            bindArgs(statement, args);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(map(resultSet));
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

    /**
     * 映射工具调用记录。
     *
     * @param resultSet 查询结果。
     * @return 返回工具调用记录。
     * @throws Exception 映射失败时抛出。
     */
    private ToolCallRecord map(ResultSet resultSet) throws Exception {
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
        record.setReadOnly(resultSet.getInt("read_only") != 0);
        record.setInterruptible(resultSet.getInt("interruptible") != 0);
        record.setSideEffecting(resultSet.getInt("side_effecting") != 0);
        record.setResultIndexable(resultSet.getInt("result_indexable") != 0);
        record.setOutputLimitBytes(resultSet.getInt("output_limit_bytes"));
        record.setResultSizeBytes(resultSet.getLong("result_size_bytes"));
        record.setExecutionPolicy(resultSet.getString("execution_policy"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setDurationMs(resultSet.getLong("duration_ms"));
        return record;
    }

    /**
     * 根据工具调用终态变化增减运行计数，重复保存同一终态时保持幂等。
     *
     * @param connection 当前数据库连接。
     * @param previousRunId 更新前的运行标识。
     * @param previousStatus 更新前的工具调用状态。
     * @param currentRunId 更新后的运行标识。
     * @param currentStatus 更新后的工具调用状态。
     * @throws Exception 更新失败时抛出。
     */
    private void updateToolCallCount(
            Connection connection,
            String previousRunId,
            String previousStatus,
            String currentRunId,
            String currentStatus)
            throws Exception {
        boolean previousCounted = isCountedToolCallStatus(previousStatus);
        boolean currentCounted = isCountedToolCallStatus(currentStatus);
        if (same(previousRunId, currentRunId)) {
            updateToolCallCount(
                    connection, currentRunId, (currentCounted ? 1 : 0) - (previousCounted ? 1 : 0));
            return;
        }
        if (previousCounted) {
            updateToolCallCount(connection, previousRunId, -1);
        }
        if (currentCounted) {
            updateToolCallCount(connection, currentRunId, 1);
        }
    }

    /**
     * 对单个运行的工具调用计数应用增量，并保证历史异常数据不会降为负数。
     *
     * @param connection 当前数据库连接。
     * @param runId 运行标识。
     * @param delta 计数增量。
     * @throws Exception 更新失败时抛出。
     */
    private void updateToolCallCount(Connection connection, String runId, int delta)
            throws Exception {
        if (runId == null || delta == 0) {
            return;
        }
        PreparedStatement statement =
                connection.prepareStatement(
                        "update agent_runs set tool_call_count = max(0, tool_call_count + ?) where run_id = ?");
        try {
            statement.setInt(1, delta);
            statement.setString(2, runId);
            statement.executeUpdate();
        } finally {
            statement.close();
        }
    }

    /**
     * 判断工具调用状态是否应计入已完成调用数量。
     *
     * @param status 工具调用状态。
     * @return completed 或 failed 返回 true。
     */
    private boolean isCountedToolCallStatus(String status) {
        return "completed".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status);
    }

    /**
     * 按空值安全方式比较两个运行标识。
     *
     * @param left 左侧运行标识。
     * @param right 右侧运行标识。
     * @return 两者相同时返回 true。
     */
    private boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    /**
     * 最佳努力追加可索引的工具结果。
     *
     * @param connection 当前数据库连接。
     * @param record 工具调用记录。
     */
    private void appendToolResultFts(Connection connection, ToolCallRecord record) {
        if (record == null || !record.isResultIndexable()) {
            return;
        }
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_run_events_fts (run_id, session_id, source_key, event_type, summary, metadata_json) values (?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getRunId());
            statement.setString(2, record.getSessionId());
            statement.setString(3, record.getSourceKey());
            statement.setString(4, "tool.result");
            statement.setString(
                    5,
                    String.valueOf(record.getToolName())
                            + " "
                            + redact(record.getResultPreview(), 8000));
            statement.setString(
                    6,
                    "{\"tool_name\":\""
                            + escapeJson(record.getToolName())
                            + "\",\"args_preview\":\""
                            + escapeJson(redact(record.getArgsPreview(), 8000))
                            + "\",\"result_ref\":\""
                            + escapeJson(redact(record.getResultRef(), 1000))
                            + "\"}");
            statement.executeUpdate();
            statement.close();
        } catch (Exception e) {
            logBestEffortFailure("tool_result_fts_append", e);
        }
    }

    /**
     * 转义 JSON 字符串内容。
     *
     * @param value 待转义文本。
     * @return 返回转义后的文本。
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
