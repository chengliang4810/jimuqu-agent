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
public class DashboardEnvController {
    private final DashboardEnvService envService;
    private final DashboardAuthService authService;

    public DashboardEnvController(DashboardEnvService envService, DashboardAuthService authService) {
        this.envService = envService;
        this.authService = authService;
    }

    @Mapping(value = "/api/env", method = MethodType.GET)
    public Map<String, Object> env() {
        return envService.getEnvVars();
    }

    @Mapping(value = "/api/env", method = MethodType.PUT)
    public Map<String, Object> set(Context context) throws Exception {
        ONode body = ONode.ofJson(context.body());
        return envService.set(body.get("key").getString(), body.get("value").getString());
    }

    @Mapping(value = "/api/env", method = MethodType.DELETE)
    public Map<String, Object> remove(Context context) throws Exception {
        ONode body = ONode.ofJson(context.body());
        return envService.remove(body.get("key").getString());
    }

    @Mapping(value = "/api/env/reveal", method = MethodType.POST)
    public Map<String, Object> reveal(Context context) throws Exception {
        if (!authService.allowReveal()) {
            throw new IllegalStateException("Reveal rate limit exceeded");
        }
        ONode body = ONode.ofJson(context.body());
        return envService.reveal(body.get("key").getString());
    }
}
