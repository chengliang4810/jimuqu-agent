package com.jimuqu.claw.agent.store;

import com.jimuqu.claw.agent.runtime.model.ReplyRoute;

public interface RouteStore {
    ReplyRoute get(String sessionId);

    void save(String sessionId, ReplyRoute route);
}
