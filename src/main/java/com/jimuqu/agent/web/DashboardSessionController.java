package com.jimuqu.agent.web;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

import java.util.Map;

/**
 * Dashboard 会话接口。
 */
@Controller
public class DashboardSessionController {
    private final DashboardSessionService sessionService;

    public DashboardSessionController(DashboardSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Mapping(value = "/api/sessions", method = MethodType.GET)
    public Map<String, Object> sessions(Context context) throws Exception {
        return sessionService.getSessions(context.paramAsInt("limit", 20), context.paramAsInt("offset", 0));
    }

    @Mapping(value = "/api/sessions/search", method = MethodType.GET)
    public Map<String, Object> search(Context context) throws Exception {
        return sessionService.searchSessions(context.param("q"));
    }

    @Mapping(value = "/api/sessions/{id}/messages", method = MethodType.GET)
    public Map<String, Object> messages(String id) throws Exception {
        return sessionService.getSessionMessages(id);
    }

    @Mapping(value = "/api/sessions/{id}", method = MethodType.DELETE)
    public Map<String, Object> delete(String id) throws Exception {
        return sessionService.deleteSession(id);
    }
}
