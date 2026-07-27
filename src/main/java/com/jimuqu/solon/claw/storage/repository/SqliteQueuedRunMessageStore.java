package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.QueuedRunMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** SQLite 排队运行消息叶子存储。 */
final class SqliteQueuedRunMessageStore extends SqliteRunStoreSupport {
    /**
     * 创建排队运行消息叶子存储。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteQueuedRunMessageStore(SqliteDatabase database) {
        super(database);
    }

    /**
     * 保存排队运行消息。
     *
     * @param message 排队运行消息。
     * @throws Exception 保存失败时抛出。
     */
    void save(QueuedRunMessage message) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into queued_run_messages (queue_id, run_id, session_id, source_key, message_text, message_json, status, busy_policy, created_at, started_at, finished_at, error) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, message.getQueueId());
            statement.setString(2, message.getRunId());
            statement.setString(3, message.getSessionId());
            statement.setString(4, message.getSourceKey());
            statement.setString(5, message.getMessageText());
            statement.setString(6, message.getMessageJson());
            statement.setString(7, message.getStatus());
            statement.setString(8, message.getBusyPolicy());
            statement.setLong(9, message.getCreatedAt());
            statement.setLong(10, message.getStartedAt());
            statement.setLong(11, message.getFinishedAt());
            statement.setString(12, redact(message.getError(), 2000));
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /**
     * 按来源与会话查找最早的待处理消息。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 会话标识。
     * @return 返回待处理消息，无记录时返回 null。
     * @throws Exception 查询失败时抛出。
     */
    QueuedRunMessage findNext(String sourceKey, String sessionId) throws Exception {
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from queued_run_messages where source_key = ? and session_id = ? and status = 'queued' order by created_at asc limit 1");
            statement.setString(1, sourceKey);
            statement.setString(2, sessionId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? map(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * 仅按来源键查找最早的待处理消息。
     *
     * @param sourceKey 渠道来源键。
     * @return 返回待处理消息，无记录时返回 null。
     * @throws Exception 查询失败时抛出。
     */
    QueuedRunMessage findNextBySourceKey(String sourceKey) throws Exception {
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from queued_run_messages where source_key = ? and status = 'queued' order by created_at asc limit 1");
            statement.setString(1, sourceKey);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? map(resultSet) : null;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * 统计来源与会话中的待处理消息。
     *
     * @param sourceKey 渠道来源键。
     * @param sessionId 会话标识。
     * @return 返回待处理消息数量。
     * @throws Exception 查询失败时抛出。
     */
    int countQueued(String sourceKey, String sessionId) throws Exception {
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select count(*) from queued_run_messages where source_key = ? and session_id = ? and status = 'queued'");
            statement.setString(1, sourceKey);
            statement.setString(2, sessionId);
            ResultSet resultSet = statement.executeQuery();
            try {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * 按可选预期状态原子更新排队消息。
     *
     * @param queueId 队列标识。
     * @param expectedStatus 预期旧状态，null 表示不限制。
     * @param status 新状态。
     * @param timestamp 状态时间。
     * @param error 错误摘要。
     * @return 返回是否成功更新唯一记录。
     * @throws Exception 更新失败时抛出。
     */
    boolean mark(String queueId, String expectedStatus, String status, long timestamp, String error)
            throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update queued_run_messages set status = ?, started_at = case when ? = 'running' then ? else started_at end, finished_at = case when ? in ('success','failed','cancelled') then ? else finished_at end, error = ? where queue_id = ? and (? is null or status = ?)");
            statement.setString(1, status);
            statement.setString(2, status);
            statement.setLong(3, timestamp);
            statement.setString(4, status);
            statement.setLong(5, timestamp);
            statement.setString(6, redact(error, 2000));
            statement.setString(7, queueId);
            statement.setString(8, expectedStatus);
            statement.setString(9, expectedStatus);
            try {
                return statement.executeUpdate() == 1;
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    /**
     * 将超时的运行中消息退回待处理状态。
     *
     * @param beforeEpochMillis 恢复阈值时间。
     * @return 返回被重排队的消息数量。
     * @throws Exception 更新失败时抛出。
     */
    int requeueStaleRunning(long beforeEpochMillis) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update queued_run_messages set status = 'queued', started_at = 0, finished_at = 0, error = null where status = 'running' and coalesce(nullif(started_at, 0), created_at) < ?");
            statement.setLong(1, beforeEpochMillis);
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
     * 映射排队运行消息。
     *
     * @param resultSet 查询结果。
     * @return 返回排队运行消息。
     * @throws Exception 映射失败时抛出。
     */
    private QueuedRunMessage map(ResultSet resultSet) throws Exception {
        QueuedRunMessage record = new QueuedRunMessage();
        record.setQueueId(resultSet.getString("queue_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setMessageText(resultSet.getString("message_text"));
        record.setMessageJson(resultSet.getString("message_json"));
        record.setStatus(resultSet.getString("status"));
        record.setBusyPolicy(resultSet.getString("busy_policy"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setStartedAt(resultSet.getLong("started_at"));
        record.setFinishedAt(resultSet.getLong("finished_at"));
        record.setError(resultSet.getString("error"));
        return record;
    }
}
