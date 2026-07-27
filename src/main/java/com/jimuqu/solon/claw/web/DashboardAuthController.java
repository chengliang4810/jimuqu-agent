package com.jimuqu.solon.claw.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

/** Dashboard 浏览器短会话签发、探测与撤销控制器。 */
@Controller
public class DashboardAuthController {
    /** Dashboard 鉴权与短会话服务。 */
    private final DashboardAuthService authService;

    /**
     * 创建 Dashboard 浏览器短会话控制器。
     *
     * @param authService Dashboard 鉴权服务。
     */
    public DashboardAuthController(DashboardAuthService authService) {
        this.authService = authService;
    }

    /**
     * 使用长期 Bearer 令牌换取 HttpOnly 短会话。
     *
     * @param context 当前请求上下文。
     * @return 短会话签发结果。
     */
    @Mapping(value = "/api/auth/session", method = MethodType.POST)
    public Map<String, Object> issueSession(Context context) {
        if (!authService.issueBrowserSession(context)) {
            return DashboardResponse.error(context, 401, "DASHBOARD_UNAUTHORIZED", "Unauthorized");
        }
        return DashboardResponse.ok(sessionData());
    }

    /**
     * 探测当前浏览器 HttpOnly 短会话是否仍然有效。
     *
     * @param context 当前请求上下文。
     * @return 当前短会话状态。
     */
    @Mapping(value = "/api/auth/session", method = MethodType.GET)
    public Map<String, Object> currentSession(Context context) {
        if (authService.authenticationMethod(context)
                != DashboardAuthService.AuthenticationMethod.SESSION) {
            return DashboardResponse.error(context, 401, "DASHBOARD_UNAUTHORIZED", "Unauthorized");
        }
        return DashboardResponse.ok(sessionData());
    }

    /**
     * 撤销当前浏览器 HttpOnly 短会话。
     *
     * @param context 当前请求上下文。
     * @return 短会话撤销结果。
     */
    @Mapping(value = "/api/auth/session", method = MethodType.DELETE)
    public Map<String, Object> revokeSession(Context context) {
        authService.revokeBrowserSession(context);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("authenticated", Boolean.FALSE);
        return DashboardResponse.ok(data);
    }

    /**
     * 构造不含票据或长期令牌的短会话响应。
     *
     * @return 当前短会话公共状态。
     */
    private Map<String, Object> sessionData() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("authenticated", Boolean.TRUE);
        data.put("auth_method", "session");
        return data;
    }
}
