package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.QueuedRunMessage;
import com.jimuqu.solon.claw.core.model.RunControlCommand;
import com.jimuqu.solon.claw.core.model.RunRecoveryRecord;
import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.support.SecretRedactor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SQLite Agent run 仓储实现。 */
public class SqliteAgentRunRepository implements AgentRunRepository {
    /** Agent run 仓储日志仅记录可降级维护失败的操作名和异常类型，避免泄露会话内容或工具结果。 */
    private static final Logger log = LoggerFactory.getLogger(SqliteAgentRunRepository.class);

    /** 用量回填只读取计费与来源字段，避免批量加载运行预览和错误正文。 */
    private static final String USAGE_RUN_COLUMNS =
            "run_id, session_id, source_key, provider, model, input_tokens, output_tokens,"
                    + " total_tokens, started_at, finished_at";

    /** 陈旧运行扫描只读取状态转换与会话恢复需要的标识字段。 */
    private static final String STALE_RUN_COLUMNS =
            "run_id, session_id, source_key, run_kind, status";

    /** 记录SQLiteAgent运行中的数据库。 */
    private final SqliteDatabase database;

    /** 运行事件叶子存储。 */
    private final SqliteRunEventStore runEventStore;

    /** 运行控制命令叶子存储。 */
    private final SqliteRunControlStore runControlStore;

    /** 排队运行消息叶子存储。 */
    private final SqliteQueuedRunMessageStore queuedRunMessageStore;

    /** 工具调用存储。 */
    private final SqliteToolCallStore toolCallStore;

    /** 子代理运行叶子存储。 */
    private final SqliteSubagentRunStore subagentRunStore;

    /** 运行恢复记录叶子存储。 */
    private final SqliteRunRecoveryStore runRecoveryStore;

    /** Agent 运行跨表清理器。 */
    private final SqliteAgentRunPruner runPruner;

    /**
     * 创建 SQLite Agent 运行仓储门面。
     *
     * @param database SQLite 数据库连接入口。
     */
    public SqliteAgentRunRepository(SqliteDatabase database) {
        this.database = database;
        this.runEventStore = new SqliteRunEventStore(database);
        this.runControlStore = new SqliteRunControlStore(database);
        this.queuedRunMessageStore = new SqliteQueuedRunMessageStore(database);
        this.toolCallStore = new SqliteToolCallStore(database);
        this.subagentRunStore = new SqliteSubagentRunStore(database);
        this.runRecoveryStore = new SqliteRunRecoveryStore(database);
        this.runPruner = new SqliteAgentRunPruner(database);
    }

    /**
     * 保存运行。
     *
     * @param record 记录参数。
     */
    @Override
    public void saveRun(AgentRunRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert into agent_runs (run_id, session_id, source_key, run_kind, parent_run_id, status, phase, busy_policy, backgrounded, input_preview, final_reply_preview, provider, model, attempts, context_estimate_tokens, context_window_tokens, compression_count, fallback_count, tool_call_count, subtask_count, input_tokens, output_tokens, total_tokens, queued_at, started_at, heartbeat_at, last_activity_at, finished_at, exit_reason, recoverable, recovery_hint, error) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                                    + "on conflict(run_id) do update set "
                                    + "session_id=excluded.session_id, source_key=excluded.source_key, run_kind=excluded.run_kind, parent_run_id=excluded.parent_run_id, "
                                    + "status=excluded.status, phase=excluded.phase, busy_policy=excluded.busy_policy, backgrounded=excluded.backgrounded, "
                                    + "input_preview=excluded.input_preview, final_reply_preview=excluded.final_reply_preview, provider=excluded.provider, model=excluded.model, "
                                    + "attempts=excluded.attempts, context_estimate_tokens=excluded.context_estimate_tokens, context_window_tokens=excluded.context_window_tokens, "
                                    + "compression_count=excluded.compression_count, fallback_count=excluded.fallback_count, "
                                    + "subtask_count=excluded.subtask_count, input_tokens=excluded.input_tokens, output_tokens=excluded.output_tokens, total_tokens=excluded.total_tokens, "
                                    + "queued_at=excluded.queued_at, started_at=excluded.started_at, heartbeat_at=excluded.heartbeat_at, last_activity_at=excluded.last_activity_at, "
                                    + "finished_at=excluded.finished_at, exit_reason=excluded.exit_reason, recoverable=excluded.recoverable, "
                                    + "recovery_hint=excluded.recovery_hint, error=excluded.error");
            statement.setString(1, record.getRunId());
            statement.setString(2, record.getSessionId());
            statement.setString(3, record.getSourceKey());
            statement.setString(4, record.getRunKind());
            statement.setString(5, record.getParentRunId());
            statement.setString(6, record.getStatus());
            statement.setString(7, record.getPhase());
            statement.setString(8, record.getBusyPolicy());
            statement.setInt(9, record.isBackgrounded() ? 1 : 0);
            statement.setString(10, redact(record.getInputPreview(), 8000));
            statement.setString(11, redact(record.getFinalReplyPreview(), 8000));
            statement.setString(12, record.getProvider());
            statement.setString(13, record.getModel());
            statement.setInt(14, record.getAttempts());
            statement.setInt(15, record.getContextEstimateTokens());
            statement.setInt(16, record.getContextWindowTokens());
            statement.setInt(17, record.getCompressionCount());
            statement.setInt(18, record.getFallbackCount());
            statement.setInt(19, Math.max(0, record.getToolCallCount()));
            statement.setInt(20, record.getSubtaskCount());
            statement.setLong(21, record.getInputTokens());
            statement.setLong(22, record.getOutputTokens());
            statement.setLong(23, record.getTotalTokens());
            statement.setLong(24, record.getQueuedAt());
            statement.setLong(25, record.getStartedAt());
            statement.setLong(26, record.getHeartbeatAt());
            statement.setLong(27, record.getLastActivityAt());
            statement.setLong(28, record.getFinishedAt());
            statement.setString(29, record.getExitReason());
            statement.setInt(30, record.isRecoverable() ? 1 : 0);
            statement.setString(31, redact(record.getRecoveryHint(), 2000));
            statement.setString(32, redact(record.getError(), 2000));
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /**
     * 查找运行。
     *
     * @param runId 运行标识。
     * @return 返回运行结果。
     */
    @Override
    public AgentRunRecord findRun(String runId) throws Exception {
        Connection connection = database.openReadConnection();
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

    /**
     * 列出根据会话。
     *
     * @param sessionId 当前会话标识。
     * @param limit 最大返回数量。
     * @return 返回根据会话列表。
     */
    @Override
    public List<AgentRunRecord> listBySession(String sessionId, int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openReadConnection();
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

    /**
     * 统计会话内已产生 token 用量或已结束模型尝试的运行次数，避免 TUI 长会话只读取最近列表造成 API 调用数低估。
     *
     * @param sessionId 当前会话标识。
     * @return 返回带用量的运行次数。
     */
    @Override
    public long countUsageRunsBySession(String sessionId) throws Exception {
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select count(*) from agent_runs where session_id = ? and (input_tokens > 0 or output_tokens > 0 or total_tokens > 0 or (attempts > 0 and finished_at > 0 and coalesce(provider, '') <> '' and coalesce(model, '') <> ''))");
            statement.setString(1, sessionId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * 按稳定游标列出已完成且包含用量的运行。
     *
     * @param beforeFinishedAt 上一页末条完成时间；小于零表示首页。
     * @param beforeRunId 上一页末条运行标识；首页可为空。
     * @param limit 最大返回数量。
     * @return 按完成时间和运行标识倒序排列的运行列表。
     */
    @Override
    public List<AgentRunRecord> listFinishedWithUsage(
            long beforeFinishedAt, String beforeRunId, int limit) throws Exception {
        if (beforeFinishedAt >= 0L && (beforeRunId == null || beforeRunId.trim().isEmpty())) {
            throw new IllegalArgumentException("用量运行分页游标缺少 runId");
        }
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select "
                                    + USAGE_RUN_COLUMNS
                                    + " from agent_runs where status = 'success'"
                                    + " and (input_tokens > 0 or output_tokens > 0 or total_tokens > 0)"
                                    + " and (? < 0 or finished_at < ?"
                                    + " or (finished_at = ? and run_id < ?))"
                                    + " order by finished_at desc, run_id desc limit ?");
            statement.setLong(1, beforeFinishedAt);
            statement.setLong(2, beforeFinishedAt);
            statement.setLong(3, beforeFinishedAt);
            statement.setString(4, beforeRunId == null ? "" : beforeRunId);
            statement.setInt(5, Math.max(1, Math.min(limit <= 0 ? 1000 : limit, 10000)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapUsageRun(resultSet));
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
     * 列出Recoverable。
     *
     * @param limit 最大返回数量。
     * @return 返回Recoverable列表。
     */
    @Override
    public List<AgentRunRecord> listRecoverable(int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openReadConnection();
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

    /**
     * 列出Active Before。
     *
     * @param beforeEpochMillis beforeEpochMillis 参数。
     * @param limit 最大返回数量。
     * @return 返回Active Before列表。
     */
    @Override
    public List<AgentRunRecord> listActiveBefore(long beforeEpochMillis, int limit)
            throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select "
                                    + STALE_RUN_COLUMNS
                                    + " from agent_runs where status in"
                                    + " ('queued','running','waiting_approval','backgrounded','paused','interrupting')"
                                    + " and coalesce(nullif(last_activity_at, 0), started_at) < ?"
                                    + " order by started_at asc limit ?");
            statement.setLong(1, beforeEpochMillis);
            statement.setInt(2, Math.max(1, Math.min(limit, 200)));
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    records.add(mapStaleRunCandidate(resultSet));
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
     * 在同一事务中选择、批量更新陈旧运行并写入恢复记录。
     *
     * @param beforeEpochMillis 陈旧判定截止时间。
     * @param now 恢复记录创建时间。
     * @param limit 单批最大处理数量。
     * @return 返回本次实际完成状态转换的原始运行记录。
     */
    @Override
    public List<AgentRunRecord> markStaleRuns(long beforeEpochMillis, long now, int limit)
            throws Exception {
        Connection connection = database.openConnection();
        boolean transactionOwner = connection.getAutoCommit();
        try {
            if (transactionOwner) {
                connection.setAutoCommit(false);
            }
            List<AgentRunRecord> stale =
                    listActiveBefore(
                            connection, beforeEpochMillis, Math.max(1, Math.min(limit, 500)));
            if (stale.isEmpty()) {
                if (transactionOwner) {
                    connection.commit();
                }
                return stale;
            }

            String recoveryHint = "服务重启或长时间无 heartbeat，已标记为可恢复。";
            StringBuilder updateSql =
                    new StringBuilder(
                            "update agent_runs set status = 'recoverable', phase = 'recovery', recoverable = 1, recovery_hint = ?, exit_reason = 'stale_heartbeat', finished_at = 0 where run_id in (");
            for (int index = 0; index < stale.size(); index++) {
                if (index > 0) {
                    updateSql.append(',');
                }
                updateSql.append('?');
            }
            updateSql.append(
                    ") and status in ('queued','running','waiting_approval','backgrounded','paused','interrupting') and coalesce(nullif(last_activity_at, 0), started_at) < ?");
            PreparedStatement update = connection.prepareStatement(updateSql.toString());
            try {
                update.setString(1, redact(recoveryHint, 2000));
                for (int index = 0; index < stale.size(); index++) {
                    update.setString(index + 2, stale.get(index).getRunId());
                }
                update.setLong(stale.size() + 2, beforeEpochMillis);
                int updated = update.executeUpdate();
                if (updated != stale.size()) {
                    throw new IllegalStateException(
                            "陈旧运行批量更新数量不一致：expected=" + stale.size() + ", actual=" + updated);
                }
            } finally {
                update.close();
            }

            PreparedStatement insertRecovery =
                    connection.prepareStatement(
                            "insert into run_recoveries (recovery_id, run_id, session_id, source_key, recovery_type, status, summary, payload_json, created_at, resolved_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            try {
                for (AgentRunRecord record : stale) {
                    insertRecovery.setString(1, com.jimuqu.solon.claw.support.IdSupport.newId());
                    insertRecovery.setString(2, record.getRunId());
                    insertRecovery.setString(3, record.getSessionId());
                    insertRecovery.setString(4, record.getSourceKey());
                    insertRecovery.setString(5, "stale_heartbeat");
                    insertRecovery.setString(6, "recoverable");
                    insertRecovery.setString(7, redact(recoveryHint, 2000));
                    insertRecovery.setString(8, null);
                    insertRecovery.setLong(9, now);
                    insertRecovery.setLong(10, 0L);
                    insertRecovery.addBatch();
                }
                insertRecovery.executeBatch();
            } finally {
                insertRecovery.close();
            }
            if (transactionOwner) {
                connection.commit();
            }
            return stale;
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
                    logBestEffortFailure("stale_run_transaction_reset", e);
                }
            }
            connection.close();
        }
    }

    /**
     * 使用指定连接查询陈旧活动运行，供事务内批处理复用。
     *
     * @param connection 当前数据库连接。
     * @param beforeEpochMillis 陈旧判定截止时间。
     * @param limit 单批最大返回数量。
     * @return 返回陈旧活动运行列表。
     */
    private List<AgentRunRecord> listActiveBefore(
            Connection connection, long beforeEpochMillis, int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        PreparedStatement statement =
                connection.prepareStatement(
                        "select "
                                + STALE_RUN_COLUMNS
                                + " from agent_runs where status in"
                                + " ('queued','running','waiting_approval','backgrounded','paused','interrupting')"
                                + " and coalesce(nullif(last_activity_at, 0), started_at) < ?"
                                + " order by started_at asc limit ?");
        statement.setLong(1, beforeEpochMillis);
        statement.setInt(2, Math.max(1, Math.min(limit, 500)));
        ResultSet resultSet = statement.executeQuery();
        try {
            while (resultSet.next()) {
                records.add(mapStaleRunCandidate(resultSet));
            }
        } finally {
            resultSet.close();
            statement.close();
        }
        return records;
    }

    /**
     * 列出Active根据来源。
     *
     * @param sourceKey 渠道来源键。
     * @param limit 最大返回数量。
     * @return 返回Active根据来源列表。
     */
    @Override
    public List<AgentRunRecord> listActiveBySource(String sourceKey, int limit) throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from agent_runs where source_key = ? and status in ('queued','running','waiting_approval','backgrounded','paused','interrupting','recoverable') order by started_at desc limit ?");
            statement.setString(1, sourceKey);
            statement.setInt(2, Math.max(1, Math.min(limit, 50)));
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

    /**
     * 搜索运行。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 当前会话标识。
     * @param runId 运行标识。
     * @param query 查询参数。
     * @param timeFrom 时间From参数。
     * @param timeTo 时间To参数。
     * @param limit 最大返回数量。
     * @return 返回运行结果。
     */
    @Override
    public List<AgentRunRecord> searchRuns(
            String sourceKey,
            String sessionId,
            String runId,
            String query,
            long timeFrom,
            long timeTo,
            int limit)
            throws Exception {
        List<AgentRunRecord> records = new ArrayList<AgentRunRecord>();
        Connection connection = database.openReadConnection();
        try {
            StringBuilder sql = new StringBuilder("select distinct r.* from agent_runs r");
            List<Object> args = new ArrayList<Object>();
            boolean hasQuery = query != null && query.trim().length() > 0;
            if (hasQuery) {
                sql.append(" left join agent_run_events e on e.run_id = r.run_id");
            }
            sql.append(" where 1 = 1");
            appendRunFilters(sql, args, sourceKey, sessionId, runId, timeFrom, timeTo);
            if (hasQuery) {
                sql.append(
                        " and (lower(coalesce(r.input_preview, '')) like ?"
                                + " or lower(coalesce(r.final_reply_preview, '')) like ?"
                                + " or lower(coalesce(r.error, '')) like ?"
                                + " or lower(coalesce(e.summary, '')) like ?"
                                + " or lower(coalesce(e.metadata_json, '')) like ?)");
                String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
                args.add(pattern);
            }
            sql.append(
                    " order by coalesce(nullif(r.last_activity_at, 0), r.started_at) desc limit ?");
            args.add(Math.max(1, Math.min(limit <= 0 ? 20 : limit, 200)));
            PreparedStatement statement = connection.prepareStatement(sql.toString());
            bindArgs(statement, args);
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

    /**
     * 追加事件。
     *
     * @param event 事件参数。
     */
    @Override
    public void appendEvent(AgentRunEventRecord event) throws Exception {
        runEventStore.appendEvent(event);
    }

    /**
     * 列出Events。
     *
     * @param runId 运行标识。
     * @return 返回Events列表。
     */
    @Override
    public List<AgentRunEventRecord> listEvents(String runId) throws Exception {
        return runEventStore.listEvents(runId);
    }

    /**
     * 搜索运行事件。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 当前会话标识。
     * @param runId 运行标识。
     * @param query 查询参数。
     * @param timeFrom 时间From参数。
     * @param timeTo 时间To参数。
     * @param limit 最大返回数量。
     * @return 返回运行事件结果。
     */
    @Override
    public List<AgentRunEventRecord> searchEvents(
            String sourceKey,
            String sessionId,
            String runId,
            String query,
            long timeFrom,
            long timeTo,
            int limit)
            throws Exception {
        return runEventStore.searchEvents(
                sourceKey, sessionId, runId, query, timeFrom, timeTo, limit);
    }

    /**
     * 保存运行Control命令。
     *
     * @param command 待执行或解析的命令文本。
     */
    @Override
    public void saveRunControlCommand(RunControlCommand command) throws Exception {
        runControlStore.save(command);
    }

    /**
     * 列出运行Control Commands。
     *
     * @param runId 运行标识。
     * @return 返回运行Control Commands列表。
     */
    @Override
    public List<RunControlCommand> listRunControlCommands(String runId) throws Exception {
        return runControlStore.list(runId);
    }

    /**
     * 查找Latest Pending命令。
     *
     * @param runId 运行标识。
     * @param command 待执行或解析的命令文本。
     * @return 返回Latest Pending命令结果。
     */
    @Override
    public RunControlCommand findLatestPendingCommand(String runId, String command)
            throws Exception {
        return runControlStore.findLatestPending(runId, command);
    }

    /**
     * 标记运行Control命令Handled。
     *
     * @param commandId 命令标识。
     * @param status 状态参数。
     * @param handledAt handledAt 参数。
     */
    @Override
    public void markRunControlCommandHandled(String commandId, String status, long handledAt)
            throws Exception {
        runControlStore.markHandled(commandId, status, handledAt);
    }

    /**
     * 保存Queued消息。
     *
     * @param message 平台消息或错误消息。
     */
    @Override
    public void saveQueuedMessage(QueuedRunMessage message) throws Exception {
        queuedRunMessageStore.save(message);
    }

    /**
     * 查找Next Queued消息。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 当前会话标识。
     * @return 返回Next Queued消息结果。
     */
    @Override
    public QueuedRunMessage findNextQueuedMessage(String sourceKey, String sessionId)
            throws Exception {
        return queuedRunMessageStore.findNext(sourceKey, sessionId);
    }

    /**
     * 仅按来源键查找Next Queued消息（不限会话），用于 goal 续轮抢占判定。
     *
     * @param sourceKey 渠道来源键。
     * @return 返回该来源键下最早的 queued 消息；无待处理消息返回 null。
     */
    @Override
    public QueuedRunMessage findNextQueuedMessageBySourceKey(String sourceKey) throws Exception {
        return queuedRunMessageStore.findNextBySourceKey(sourceKey);
    }

    /**
     * 执行次数排队Messages相关逻辑。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 当前会话标识。
     * @return 返回次数Queued Messages结果。
     */
    @Override
    public int countQueuedMessages(String sourceKey, String sessionId) throws Exception {
        return queuedRunMessageStore.countQueued(sourceKey, sessionId);
    }

    /**
     * 标记Queued消息。
     *
     * @param queueId 队列标识。
     * @param status 状态参数。
     * @param timestamp 请求携带的时间戳。
     * @param error 错误参数。
     */
    @Override
    public void markQueuedMessage(String queueId, String status, long timestamp, String error)
            throws Exception {
        markQueuedMessage(queueId, null, status, timestamp, error);
    }

    /** 按预期状态原子更新排队消息，返回值用于确认当前 drain 是否取得状态所有权。 */
    @Override
    public boolean markQueuedMessage(
            String queueId, String expectedStatus, String status, long timestamp, String error)
            throws Exception {
        return queuedRunMessageStore.mark(queueId, expectedStatus, status, timestamp, error);
    }

    /** 将超过恢复阈值的 running 队列项退回 queued，并清理上次执行痕迹。 */
    @Override
    public int requeueStaleRunningMessages(long beforeEpochMillis) throws Exception {
        return queuedRunMessageStore.requeueStaleRunning(beforeEpochMillis);
    }

    /**
     * 保存工具Call。
     *
     * @param record 记录参数。
     */
    @Override
    public void saveToolCall(ToolCallRecord record) throws Exception {
        toolCallStore.save(record);
    }

    /**
     * 列出工具Calls。
     *
     * @param runId 运行标识。
     * @return 返回工具Calls列表。
     */
    @Override
    public List<ToolCallRecord> listToolCalls(String runId) throws Exception {
        return toolCallStore.list(runId);
    }

    /**
     * 搜索工具Calls。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 当前会话标识。
     * @param runId 运行标识。
     * @param toolName 工具名称。
     * @param query 查询参数。
     * @param timeFrom 时间From参数。
     * @param timeTo 时间To参数。
     * @param limit 最大返回数量。
     * @return 返回工具Calls结果。
     */
    @Override
    public List<ToolCallRecord> searchToolCalls(
            String sourceKey,
            String sessionId,
            String runId,
            String toolName,
            String query,
            long timeFrom,
            long timeTo,
            int limit)
            throws Exception {
        return toolCallStore.search(
                sourceKey, sessionId, runId, toolName, query, timeFrom, timeTo, limit);
    }

    /**
     * 保存Subagent运行。
     *
     * @param record 记录参数。
     */
    @Override
    public void saveSubagentRun(SubagentRunRecord record) throws Exception {
        subagentRunStore.save(record);
    }

    /**
     * 列出Subagents。
     *
     * @param parentRunId parent运行标识。
     * @return 返回Subagents列表。
     */
    @Override
    public List<SubagentRunRecord> listSubagents(String parentRunId) throws Exception {
        return subagentRunStore.list(parentRunId);
    }

    /**
     * 将数据库中仍标记为活动、但已没有当前进程控制句柄的子 Agent 收敛为已中断。
     *
     * @param now 当前时间戳，用作遗留记录的结束时间和最后心跳时间。
     * @return 被收敛的子 Agent 记录数量。
     */
    @Override
    public int markActiveSubagentsInterrupted(long now) throws Exception {
        return subagentRunStore.markActiveInterrupted(now);
    }

    /**
     * 保存Recovery。
     *
     * @param record 记录参数。
     */
    @Override
    public void saveRecovery(RunRecoveryRecord record) throws Exception {
        runRecoveryStore.save(record);
    }

    /**
     * 列出Recoveries。
     *
     * @param runId 运行标识。
     * @return 返回Recoveries列表。
     */
    @Override
    public List<RunRecoveryRecord> listRecoveries(String runId) throws Exception {
        return runRecoveryStore.list(runId);
    }

    /**
     * 执行pruneBefore相关逻辑。
     *
     * @param beforeEpochMillis beforeEpochMillis 参数。
     */
    @Override
    public void pruneBefore(long beforeEpochMillis) throws Exception {
        runPruner.pruneBefore(beforeEpochMillis);
    }

    /**
     * 执行map运行相关逻辑。
     *
     * @param resultSet 结果Set响应或执行结果。
     * @return 返回map运行结果。
     */
    private AgentRunRecord mapRun(ResultSet resultSet) throws Exception {
        AgentRunRecord record = new AgentRunRecord();
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setRunKind(resultSet.getString("run_kind"));
        record.setParentRunId(resultSet.getString("parent_run_id"));
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

    /**
     * 映射用量回填所需的轻量运行记录。
     *
     * @param resultSet 用量运行查询结果。
     * @return 仅包含计费与来源字段的运行记录。
     */
    private AgentRunRecord mapUsageRun(ResultSet resultSet) throws Exception {
        AgentRunRecord record = new AgentRunRecord();
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setProvider(resultSet.getString("provider"));
        record.setModel(resultSet.getString("model"));
        record.setInputTokens(resultSet.getLong("input_tokens"));
        record.setOutputTokens(resultSet.getLong("output_tokens"));
        record.setTotalTokens(resultSet.getLong("total_tokens"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        return record;
    }

    /**
     * 映射陈旧运行状态转换与会话恢复所需的轻量候选。
     *
     * @param resultSet 陈旧运行查询结果。
     * @return 仅包含标识、来源、类型与原状态的运行记录。
     */
    private AgentRunRecord mapStaleRunCandidate(ResultSet resultSet) throws Exception {
        AgentRunRecord record = new AgentRunRecord();
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setRunKind(resultSet.getString("run_kind"));
        record.setStatus(resultSet.getString("status"));
        return record;
    }

    /**
     * 追加运行Filters。
     *
     * @param sql sql 参数。
     * @param args 工具或命令参数。
     * @param sourceKey 渠道来源键。
     * @param sessionId 当前会话标识。
     * @param runId 运行标识。
     * @param timeFrom 时间From参数。
     * @param timeTo 时间To参数。
     */
    private void appendRunFilters(
            StringBuilder sql,
            List<Object> args,
            String sourceKey,
            String sessionId,
            String runId,
            long timeFrom,
            long timeTo) {
        if (sourceKey != null && sourceKey.trim().length() > 0) {
            sql.append(" and r.source_key = ?");
            args.add(sourceKey);
        }
        if (sessionId != null && sessionId.trim().length() > 0) {
            sql.append(" and r.session_id = ?");
            args.add(sessionId);
        }
        if (runId != null && runId.trim().length() > 0) {
            sql.append(" and r.run_id = ?");
            args.add(runId);
        }
        if (timeFrom > 0) {
            sql.append(" and coalesce(nullif(r.last_activity_at, 0), r.started_at) >= ?");
            args.add(Long.valueOf(timeFrom));
        }
        if (timeTo > 0) {
            sql.append(" and coalesce(nullif(r.last_activity_at, 0), r.started_at) <= ?");
            args.add(Long.valueOf(timeTo));
        }
    }

    /**
     * 执行bind参数相关逻辑。
     *
     * @param statement statement 参数。
     * @param args 工具或命令参数。
     */
    private void bindArgs(PreparedStatement statement, List<Object> args) throws Exception {
        for (int i = 0; i < args.size(); i++) {
            Object value = args.get(i);
            if (value instanceof Long) {
                statement.setLong(i + 1, ((Long) value).longValue());
            } else if (value instanceof Integer) {
                statement.setInt(i + 1, ((Integer) value).intValue());
            } else {
                statement.setString(i + 1, value == null ? null : String.valueOf(value));
            }
        }
    }

    /**
     * 记录可降级的仓储维护失败，不输出运行标识、正文、参数预览或结果摘要。
     *
     * @param operation 内部维护操作名。
     * @param error 维护失败异常。
     */
    private static void logBestEffortFailure(String operation, Exception error) {
        log.debug(
                "Agent run 仓储可降级维护失败，已跳过非主链更新: operation={}, error={}",
                operation,
                exceptionSummary(error));
    }

    /**
     * 提取异常类型摘要，避免数据库驱动消息携带 SQL 参数或业务内容。
     *
     * @param error 待记录的异常。
     * @return 返回异常类型摘要。
     */
    private static String exceptionSummary(Exception error) {
        return error == null ? "unknown" : error.getClass().getSimpleName();
    }

    /**
     * 脱敏文本中的密钥、令牌和敏感路径。
     *
     * @param value 待规范化或校验的原始值。
     * @param maxLength 最大保留字符数。
     * @return 返回redact结果。
     */
    private String redact(String value, int maxLength) {
        return value == null
                ? null
                : SecretRedactor.redactSensitivePaths(SecretRedactor.redact(value, maxLength));
    }
}
