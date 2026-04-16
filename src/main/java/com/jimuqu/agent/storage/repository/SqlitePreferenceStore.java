package com.jimuqu.agent.storage.repository;

import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SqlitePreferenceStore 实现。
 */
@RequiredArgsConstructor
public class SqlitePreferenceStore {
    private final SqliteDatabase database;

    public boolean isToolEnabled(String sourceKey, String toolName) throws SQLException {
        return readBoolean("tool_toggles", "tool_name", sourceKey, toolName, true);
    }

    public void setToolEnabled(String sourceKey, String toolName, boolean enabled) throws SQLException {
        writeBoolean("tool_toggles", "tool_name", sourceKey, toolName, enabled);
    }

    public boolean isSkillEnabled(String sourceKey, String skillName) throws SQLException {
        return readBoolean("skill_states", "skill_name", sourceKey, skillName, true);
    }

    public void setSkillEnabled(String sourceKey, String skillName, boolean enabled) throws SQLException {
        writeBoolean("skill_states", "skill_name", sourceKey, skillName, enabled);
    }

    private boolean readBoolean(String tableName, String nameColumn, String sourceKey, String nameValue, boolean defaultValue) throws SQLException {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select enabled from " + tableName + " where source_key = ? and " + nameColumn + " = ?");
            statement.setString(1, sourceKey);
            statement.setString(2, nameValue);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return resultSet.getInt(1) == 1;
                }
                return defaultValue;
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private void writeBoolean(String tableName, String nameColumn, String sourceKey, String nameValue, boolean enabled) throws SQLException {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into " + tableName + " (source_key, " + nameColumn + ", enabled) values (?, ?, ?)");
            statement.setString(1, sourceKey);
            statement.setString(2, nameValue);
            statement.setInt(3, enabled ? 1 : 0);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }
}
