package com.jimuqu.agent.storage.repository;

import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.support.IdSupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * SqliteSessionRepository 实现。
 */
public class SqliteSessionRepository implements SessionRepository {
    private final SqliteDatabase database;

    public SqliteSessionRepository(SqliteDatabase database) {
        this.database = database;
    }

    public SessionRecord getBoundSession(String sourceKey) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select session_id from bindings where source_key = ?");
            statement.setString(1, sourceKey);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return findById(resultSet.getString(1));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }

        return null;
    }

    public SessionRecord bindNewSession(String sourceKey) throws Exception {
        long now = System.currentTimeMillis();
        SessionRecord record = new SessionRecord();
        record.setSessionId(IdSupport.newId());
        record.setSourceKey(sourceKey);
        record.setBranchName("main");
        record.setNdjson("");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        save(record);
        bindSource(sourceKey, record.getSessionId());
        return record;
    }

    public void bindSource(String sourceKey, String sessionId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into bindings (source_key, session_id) values (?, ?)");
            statement.setString(1, sourceKey);
            statement.setString(2, sessionId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    public SessionRecord cloneSession(String sourceKey, String sourceSessionId, String branchName) throws Exception {
        SessionRecord source = findById(sourceSessionId);
        if (source == null) {
            return bindNewSession(sourceKey);
        }

        long now = System.currentTimeMillis();
        SessionRecord clone = new SessionRecord();
        clone.setSessionId(IdSupport.newId());
        clone.setSourceKey(sourceKey);
        clone.setParentSessionId(source.getSessionId());
        clone.setBranchName(branchName);
        clone.setModelOverride(source.getModelOverride());
        clone.setNdjson(source.getNdjson());
        clone.setCreatedAt(now);
        clone.setUpdatedAt(now);
        save(clone);
        bindSource(sourceKey, clone.getSessionId());
        return clone;
    }

    public SessionRecord findById(String sessionId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select session_id, source_key, branch_name, parent_session_id, model_override, ndjson, created_at, updated_at from sessions where session_id = ?");
            statement.setString(1, sessionId);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return map(resultSet);
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }

        return null;
    }

    public SessionRecord findBySourceAndBranch(String sourceKey, String branchName) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select session_id, source_key, branch_name, parent_session_id, model_override, ndjson, created_at, updated_at from sessions where source_key = ? and branch_name = ? order by updated_at desc limit 1");
            statement.setString(1, sourceKey);
            statement.setString(2, branchName);
            ResultSet resultSet = statement.executeQuery();
            try {
                if (resultSet.next()) {
                    return map(resultSet);
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }

        return null;
    }

    public void save(SessionRecord sessionRecord) throws Exception {
        long updatedAt = sessionRecord.getUpdatedAt() > 0 ? sessionRecord.getUpdatedAt() : System.currentTimeMillis();
        long createdAt = sessionRecord.getCreatedAt() > 0 ? sessionRecord.getCreatedAt() : updatedAt;

        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into sessions (session_id, source_key, branch_name, parent_session_id, model_override, ndjson, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, sessionRecord.getSessionId());
            statement.setString(2, sessionRecord.getSourceKey());
            statement.setString(3, sessionRecord.getBranchName());
            statement.setString(4, sessionRecord.getParentSessionId());
            statement.setString(5, sessionRecord.getModelOverride());
            statement.setString(6, sessionRecord.getNdjson());
            statement.setLong(7, createdAt);
            statement.setLong(8, updatedAt);
            statement.executeUpdate();
            statement.close();
            sessionRecord.setCreatedAt(createdAt);
            sessionRecord.setUpdatedAt(updatedAt);
        } finally {
            connection.close();
        }
    }

    public List<SessionRecord> search(String keyword, int limit) throws Exception {
        List<SessionRecord> results = new ArrayList<SessionRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select session_id, source_key, branch_name, parent_session_id, model_override, ndjson, created_at, updated_at from sessions where ndjson like ? order by updated_at desc limit ?");
            statement.setString(1, "%" + keyword + "%");
            statement.setInt(2, limit);
            ResultSet resultSet = statement.executeQuery();
            try {
                while (resultSet.next()) {
                    results.add(map(resultSet));
                }
            } finally {
                resultSet.close();
                statement.close();
            }
        } finally {
            connection.close();
        }

        return results;
    }

    public void setModelOverride(String sessionId, String modelOverride) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("update sessions set model_override = ?, updated_at = ? where session_id = ?");
            statement.setString(1, modelOverride);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, sessionId);
            statement.executeUpdate();
            statement.close();
        } finally {
            connection.close();
        }
    }

    private SessionRecord map(ResultSet resultSet) throws Exception {
        SessionRecord record = new SessionRecord();
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setBranchName(resultSet.getString("branch_name"));
        record.setParentSessionId(resultSet.getString("parent_session_id"));
        record.setModelOverride(resultSet.getString("model_override"));
        record.setNdjson(resultSet.getString("ndjson"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setUpdatedAt(resultSet.getLong("updated_at"));
        return record;
    }
}
