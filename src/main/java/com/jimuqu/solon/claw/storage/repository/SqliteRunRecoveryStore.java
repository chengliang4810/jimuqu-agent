package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.RunRecoveryRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** SQLite 运行恢复记录叶子存储。 */
final class SqliteRunRecoveryStore extends SqliteRunStoreSupport {
    /**
     * 创建运行恢复记录叶子存储。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteRunRecoveryStore(SqliteDatabase database) {
        super(database);
    }

    /**
     * 保存运行恢复记录。
     *
     * @param record 运行恢复记录。
     * @throws Exception 保存失败时抛出。
     */
    void save(RunRecoveryRecord record) throws Exception {
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
            statement.setString(7, redact(record.getSummary(), 2000));
            statement.setString(8, redact(record.getPayloadJson(), 4000));
            statement.setLong(9, record.getCreatedAt());
            statement.setLong(10, record.getResolvedAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /**
     * 按运行标识列出恢复记录。
     *
     * @param runId 运行标识。
     * @return 返回按创建时间升序排列的恢复记录。
     * @throws Exception 查询失败时抛出。
     */
    List<RunRecoveryRecord> list(String runId) throws Exception {
        List<RunRecoveryRecord> records = new ArrayList<RunRecoveryRecord>();
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from run_recoveries where run_id = ? order by created_at asc");
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
     * 映射运行恢复记录。
     *
     * @param resultSet 查询结果。
     * @return 返回运行恢复记录。
     * @throws Exception 映射失败时抛出。
     */
    private RunRecoveryRecord map(ResultSet resultSet) throws Exception {
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
}
