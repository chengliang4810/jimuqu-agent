package com.jimuqu.agent.core;

import java.util.List;

public interface SessionRepository {
    SessionRecord getBoundSession(String sourceKey) throws Exception;

    SessionRecord bindNewSession(String sourceKey) throws Exception;

    void bindSource(String sourceKey, String sessionId) throws Exception;

    SessionRecord cloneSession(String sourceKey, String sourceSessionId, String branchName) throws Exception;

    SessionRecord findById(String sessionId) throws Exception;

    SessionRecord findBySourceAndBranch(String sourceKey, String branchName) throws Exception;

    void save(SessionRecord sessionRecord) throws Exception;

    List<SessionRecord> search(String keyword, int limit) throws Exception;

    void setModelOverride(String sessionId, String modelOverride) throws Exception;
}
