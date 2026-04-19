package com.jimuqu.agent.web;

import cn.hutool.core.io.IoUtil;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Dashboard SPA 页面入口。
 */
@Controller
public class DashboardPageController {
    private final DashboardAuthService authService;

    public DashboardPageController(DashboardAuthService authService) {
        this.authService = authService;
    }

    @Mapping("/")
    public void index(Context context) {
        renderIndex(context);
    }

    @Mapping("/status")
    public void status(Context context) {
        renderIndex(context);
    }

    @Mapping("/sessions")
    public void sessions(Context context) {
        renderIndex(context);
    }

    @Mapping("/analytics")
    public void analytics(Context context) {
        renderIndex(context);
    }

    @Mapping("/logs")
    public void logs(Context context) {
        renderIndex(context);
    }

    @Mapping("/cron")
    public void cron(Context context) {
        renderIndex(context);
    }

    @Mapping("/skills")
    public void skills(Context context) {
        renderIndex(context);
    }

    @Mapping("/config")
    public void config(Context context) {
        renderIndex(context);
    }

    @Mapping("/env")
    public void env(Context context) {
        renderIndex(context);
    }

    private void renderIndex(Context context) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("static/index.html");
        if (stream == null) {
            context.status(503);
            context.output("Dashboard frontend not built");
            return;
        }

        String html = IoUtil.read(stream, StandardCharsets.UTF_8);
        context.contentType("text/html;charset=UTF-8");
        context.output(authService.injectToken(html));
    }
}
