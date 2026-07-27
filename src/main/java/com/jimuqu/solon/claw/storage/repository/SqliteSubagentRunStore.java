package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** SQLite 子代理运行叶子存储。 */
final class SqliteSubagentRunStore extends SqliteRunStoreSupport {
    /**
     * 创建子代理运行叶子存储。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteSubagentRunStore(SqliteDatabase database) {
        super(database);
    }

    /**
     * 保存子代理运行记录。
     *
     * @param record 子代理运行记录。
     * @throws Exception 保存失败时抛出。
     */
    void save(SubagentRunRecord record) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into subagent_runs (subagent_id, parent_run_id, child_run_id, parent_source_key, child_source_key, session_id, name, goal_preview, status, active, interrupt_requested, depth, task_index, output_tail_json, error, started_at, finished_at, heartbeat_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, record.getSubagentId());
            statement.setString(2, record.getParentRunId());
            statement.setString(3, record.getChildRunId());
            statement.setString(4, record.getParentSourceKey());
            statement.setString(5, record.getChildSourceKey());
            statement.setString(6, record.getSessionId());
            statement.setString(7, record.getName());
            statement.setString(8, redact(record.getGoalPreview(), 1000));
            statement.setString(9, record.getStatus());
            statement.setInt(10, record.isActive() ? 1 : 0);
            statement.setInt(11, record.isInterruptRequested() ? 1 : 0);
            statement.setInt(12, record.getDepth());
            statement.setInt(13, record.getTaskIndex());
            statement.setString(14, redact(record.getOutputTailJson(), 4000));
            statement.setString(15, redact(record.getError(), 2000));
            statement.setLong(16, record.getStartedAt());
            statement.setLong(17, record.getFinishedAt());
            statement.setLong(18, record.getHeartbeatAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /**
     * 按父运行标识列出子代理记录。
     *
     * @param parentRunId 父运行标识。
     * @return 返回按开始时间升序排列的子代理记录。
     * @throws Exception 查询失败时抛出。
     */
    List<SubagentRunRecord> list(String parentRunId) throws Exception {
        List<SubagentRunRecord> records = new ArrayList<SubagentRunRecord>();
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from subagent_runs where parent_run_id = ? order by started_at asc");
            statement.setString(1, parentRunId);
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
     * 将遗留活动子代理统一标记为已中断。
     *
     * @param now 当前时间。
     * @return 返回被收敛的记录数量。
     * @throws Exception 更新失败时抛出。
     */
    int markActiveInterrupted(long now) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update subagent_runs set status = 'interrupted', active = 0, interrupt_requested = 1, finished_at = ?, heartbeat_at = ? where active = 1");
            statement.setLong(1, now);
            statement.setLong(2, now);
            try {
                return statement.executeUpdate();
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * 映射子代理运行记录。
     *
     * @param resultSet 查询结果。
     * @return 返回子代理运行记录。
     * @throws Exception 映射失败时抛出。
     */
    private SubagentRunRecord map(ResultSet resultSet) throws Exception {
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
        record.setActive(resultSet.getInt("active") != 0);
        record.setInterruptRequested(resultSet.getInt("interrupt_requested") != 0);
        record.setDepth(resultSet.getInt("depth"));
        record.setTaskIndex(resultSet.getInt("task_index"));
        record.setOutputTailJson(resultSet.getString("output_tail_json"));
        record.setError(resultSet.getString("error"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setHeartbeatAt(resultSet.getLong("heartbeat_at"));
        return record;
    }
}
