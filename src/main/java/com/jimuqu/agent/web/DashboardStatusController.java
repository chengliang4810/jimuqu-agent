package com.jimuqu.agent.web;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.MethodType;

import java.util.Map;

/**
 * Dashboard 状态接口。
 */
@Controller
public class DashboardStatusController {
    private final DashboardStatusService statusService;

    public DashboardStatusController(DashboardStatusService statusService) {
        this.statusService = statusService;
    }

    @Mapping(value = "/api/status", method = MethodType.GET)
    public Map<String, Object> status() throws Exception {
        return statusService.getStatus();
    }

    @Mapping(value = "/api/model/info", method = MethodType.GET)
    public Map<String, Object> modelInfo() {
        return statusService.getModelInfo();
    }
}
