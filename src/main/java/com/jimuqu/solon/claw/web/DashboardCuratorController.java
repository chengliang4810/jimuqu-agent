package com.jimuqu.solon.claw.web;

import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard Curator endpoints. */
@Controller
public class DashboardCuratorController {
    private final DashboardCuratorService curatorService;

    public DashboardCuratorController(DashboardCuratorService curatorService) {
        this.curatorService = curatorService;
    }

    @Mapping(value = "/api/hermes/curator", method = MethodType.GET)
    public Map<String, Object> list(Context context) throws Exception {
        return DashboardResponse.ok(curatorService.list(context.paramAsInt("limit", 20)));
    }

    @Mapping(value = "/api/hermes/curator/run", method = MethodType.POST)
    public Map<String, Object> run(Context context) throws Exception {
        return DashboardResponse.ok(
                curatorService.run(Boolean.parseBoolean(context.param("force"))));
    }

    @Mapping(value = "/api/hermes/curator/{reportId}", method = MethodType.GET)
    public Map<String, Object> detail(String reportId) throws Exception {
        return DashboardResponse.ok(curatorService.detail(reportId));
    }
}
