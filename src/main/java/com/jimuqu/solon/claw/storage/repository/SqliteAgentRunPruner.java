package com.jimuqu.solon.claw.storage.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

/** SQLite Agent 运行跨表清理器，保持既有删除顺序与提交语义。 */
final class SqliteAgentRunPruner extends SqliteRunStoreSupport {
    /**
     * 创建 Agent 运行跨表清理器。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteAgentRunPruner(SqliteDatabase database) {
        super(database);
    }

    /**
     * 清理早于指定时间的运行及其关联记录。
     *
     * @param beforeEpochMillis 清理阈值时间。
     * @throws Exception 清理失败时抛出。
     */
    void pruneBefore(long beforeEpochMillis) throws Exception {
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
}
