package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.RunControlCommand;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** SQLite 运行控制命令叶子存储。 */
final class SqliteRunControlStore extends SqliteRunStoreSupport {
    /**
     * 创建运行控制命令叶子存储。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteRunControlStore(SqliteDatabase database) {
        super(database);
    }

    /**
     * 保存运行控制命令。
     *
     * @param command 运行控制命令。
     * @throws Exception 保存失败时抛出。
     */
    void save(RunControlCommand command) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "insert or replace into run_control_commands (command_id, run_id, source_key, command, payload_json, status, created_at, handled_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, command.getCommandId());
            statement.setString(2, command.getRunId());
            statement.setString(3, command.getSourceKey());
            statement.setString(4, command.getCommand());
            statement.setString(5, command.getPayloadJson());
            statement.setString(6, command.getStatus());
            statement.setLong(7, command.getCreatedAt());
            statement.setLong(8, command.getHandledAt());
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /**
     * 按运行标识列出控制命令。
     *
     * @param runId 运行标识。
     * @return 返回按创建时间升序排列的命令。
     * @throws Exception 查询失败时抛出。
     */
    List<RunControlCommand> list(String runId) throws Exception {
        List<RunControlCommand> records = new ArrayList<RunControlCommand>();
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from run_control_commands where run_id = ? order by created_at asc");
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
     * 查找运行的最新待处理命令。
     *
     * @param runId 运行标识。
     * @param command 命令名称。
     * @return 返回最新待处理命令，无记录时返回 null。
     * @throws Exception 查询失败时抛出。
     */
    RunControlCommand findLatestPending(String runId, String command) throws Exception {
        Connection connection = database.openReadConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "select * from run_control_commands where run_id = ? and command = ? and status = 'pending' order by created_at desc limit 1");
            statement.setString(1, runId);
            statement.setString(2, command);
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
     * 标记运行控制命令已处理。
     *
     * @param commandId 命令标识。
     * @param status 处理状态。
     * @param handledAt 处理时间。
     * @throws Exception 更新失败时抛出。
     */
    void markHandled(String commandId, String status, long handledAt) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement =
                    connection.prepareStatement(
                            "update run_control_commands set status = ?, handled_at = ? where command_id = ?");
            statement.setString(1, status);
            statement.setLong(2, handledAt);
            statement.setString(3, commandId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    /**
     * 映射运行控制命令。
     *
     * @param resultSet 查询结果。
     * @return 返回运行控制命令。
     * @throws Exception 映射失败时抛出。
     */
    private RunControlCommand map(ResultSet resultSet) throws Exception {
        RunControlCommand record = new RunControlCommand();
        record.setCommandId(resultSet.getString("command_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setCommand(resultSet.getString("command"));
        record.setPayloadJson(resultSet.getString("payload_json"));
        record.setStatus(resultSet.getString("status"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setHandledAt(resultSet.getLong("handled_at"));
        return record;
    }
}
