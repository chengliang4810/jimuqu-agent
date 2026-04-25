package com.jimuqu.agent.web;

import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

import java.util.Map;

/**
 * Dashboard 环境变量接口。
 */
@Controller
public class DashboardRuntimeConfigController {
    private final DashboardRuntimeConfigService runtimeConfigService;
    private final DashboardAuthService authService;

    public DashboardRuntimeConfigController(DashboardRuntimeConfigService runtimeConfigService, DashboardAuthService authService) {
        this.runtimeConfigService = runtimeConfigService;
        this.authService = authService;
    }

    @Mapping(value = "/api/runtime-config", method = MethodType.GET)
    public Map<String, Object> configItems() {
        return DashboardResponse.ok(runtimeConfigService.getConfigItems());
    }

    @Mapping(value = "/api/runtime-config", method = MethodType.PUT)
    public Map<String, Object> set(Context context) throws Exception {
        ONode body = ONode.ofJson(context.body());
        return DashboardResponse.ok(runtimeConfigService.set(body.get("key").getString(), body.get("value").getString()));
    }

    @Mapping(value = "/api/runtime-config", method = MethodType.DELETE)
    public Map<String, Object> remove(Context context) throws Exception {
        ONode body = ONode.ofJson(context.body());
        return DashboardResponse.ok(runtimeConfigService.remove(body.get("key").getString()));
    }

    @Mapping(value = "/api/runtime-config/reveal", method = MethodType.POST)
    public Map<String, Object> reveal(Context context) throws Exception {
        if (!authService.allowReveal()) {
            throw new IllegalStateException("Reveal rate limit exceeded");
        }
        ONode body = ONode.ofJson(context.body());
        return DashboardResponse.ok(runtimeConfigService.reveal(body.get("key").getString()));
    }
}
