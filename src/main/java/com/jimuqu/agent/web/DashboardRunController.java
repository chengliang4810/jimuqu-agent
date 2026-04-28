package com.jimuqu.agent.web;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

import java.util.Map;

/**
 * Dashboard Agent run 接口。
 */
@Controller
public class DashboardRunController {
    private final DashboardRunService dashboardRunService;

    public DashboardRunController(DashboardRunService dashboardRunService) {
        this.dashboardRunService = dashboardRunService;
    }

    @Mapping(value = "/api/runs/{runId}", method = MethodType.GET)
    public Map<String, Object> run(String runId) throws Exception {
        return DashboardResponse.ok(dashboardRunService.run(runId));
    }

    @Mapping(value = "/api/runs/{runId}/events", method = MethodType.GET)
    public Map<String, Object> events(String runId) throws Exception {
        return DashboardResponse.ok(dashboardRunService.events(runId));
    }

    @Mapping(value = "/api/sessions/{sessionId}/runs", method = MethodType.GET)
    public Map<String, Object> sessionRuns(String sessionId, Context context) throws Exception {
        return DashboardResponse.ok(dashboardRunService.sessionRuns(sessionId, context.paramAsInt("limit", 20)));
    }
}
