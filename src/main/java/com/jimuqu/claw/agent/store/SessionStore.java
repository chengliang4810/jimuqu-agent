package com.jimuqu.claw.agent.store;

import com.jimuqu.claw.agent.runtime.model.SessionRecord;

public interface SessionStore {
    SessionRecord get(String sessionId);

    void save(SessionRecord record);
}
