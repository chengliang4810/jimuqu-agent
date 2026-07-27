package com.jimuqu.solon.claw.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextEmpty;

/** Dashboard HttpOnly 短会话签发、过期、轮换和容量边界测试。 */
public class DashboardAuthSessionServiceTest {
    /** 验证长期 Bearer 只换取具备严格属性的 256 位 HttpOnly 短会话。 */
    @Test
    void shouldIssueStrictHttpOnlySessionWithoutPersistingLongLivedToken() {
        AppConfig config = config("long-lived-dashboard-token");
        AtomicLong now = new AtomicLong(1_000L);
        DashboardAuthService service =
                new DashboardAuthService(config, new SecureRandom(), now::get);
        ContextEmpty issue = bearerContext("long-lived-dashboard-token");
        issue.headerMap().put("X-Forwarded-Proto", "https");

        assertThat(service.issueBrowserSession(issue)).isTrue();

        String setCookie = issue.headerOfResponse("Set-Cookie");
        String ticket = cookieValue(setCookie);
        assertThat(ticket).hasSize(43);
        assertThat(setCookie)
                .contains("Max-Age=28800")
                .contains("Path=/api")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Secure")
                .doesNotContain("long-lived-dashboard-token");
        ContextEmpty sessionRequest = sessionContext(ticket);
        assertThat(service.authenticationMethod(sessionRequest))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.SESSION);
    }

    /** 验证显式错误 Bearer 不会回退同一请求携带的有效 Cookie。 */
    @Test
    void shouldNotFallBackToCookieAfterInvalidBearer() {
        AppConfig config = config("correct-token");
        DashboardAuthService service = new DashboardAuthService(config);
        ContextEmpty issue = bearerContext("correct-token");
        assertThat(service.issueBrowserSession(issue)).isTrue();
        String ticket = cookieValue(issue.headerOfResponse("Set-Cookie"));
        ContextEmpty request = sessionContext(ticket);
        request.headerMap().put("Authorization", "Bearer wrong-token");

        assertThat(service.authenticationMethod(request))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.NONE);
    }

    /** 验证长期令牌轮换后旧短会话立即失效，新 Bearer 仍可签发新会话。 */
    @Test
    void shouldInvalidateSessionWhenLongLivedTokenRotates() {
        AppConfig config = config("token-before-rotation");
        DashboardAuthService service = new DashboardAuthService(config);
        ContextEmpty issue = bearerContext("token-before-rotation");
        assertThat(service.issueBrowserSession(issue)).isTrue();
        String ticket = cookieValue(issue.headerOfResponse("Set-Cookie"));

        config.getDashboard().setAccessToken("token-after-rotation");

        assertThat(service.authenticationMethod(sessionContext(ticket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.NONE);
        ContextEmpty replacement = bearerContext("token-after-rotation");
        assertThat(service.issueBrowserSession(replacement)).isTrue();
    }

    /** 验证令牌 A→B→A 且中间没有认证请求时，旧 A 会话也不能复活。 */
    @Test
    void shouldNotRestoreSessionWhenTokenRotatesAwayAndBackWithoutAuthentication() {
        AppConfig config = config("token-generation-a");
        DashboardAuthService service = new DashboardAuthService(config);
        ContextEmpty issue = bearerContext("token-generation-a");
        assertThat(service.issueBrowserSession(issue)).isTrue();
        String ticket = cookieValue(issue.headerOfResponse("Set-Cookie"));

        config.getDashboard().setAccessToken("token-generation-b");
        config.getDashboard().setAccessToken("token-generation-a");

        assertThat(service.authenticationMethod(sessionContext(ticket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.NONE);
    }

    /** 验证用相同值刷新配置不会无故撤销仍然有效的 Dashboard 短会话。 */
    @Test
    void shouldKeepSessionWhenAccessTokenValueDoesNotChange() {
        AppConfig config = config("stable-token");
        DashboardAuthService service = new DashboardAuthService(config);
        ContextEmpty issue = bearerContext("stable-token");
        assertThat(service.issueBrowserSession(issue)).isTrue();
        String ticket = cookieValue(issue.headerOfResponse("Set-Cookie"));

        config.getDashboard().setAccessToken("stable-token");

        assertThat(service.authenticationMethod(sessionContext(ticket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.SESSION);
    }

    /** 验证短会话同时执行 30 分钟空闲超时和 8 小时绝对超时。 */
    @Test
    void shouldEnforceIdleAndAbsoluteSessionTimeouts() {
        AppConfig config = config("timeout-token");
        AtomicLong now = new AtomicLong(10_000L);
        DashboardAuthService idleService =
                new DashboardAuthService(config, new SecureRandom(), now::get);
        ContextEmpty idleIssue = bearerContext("timeout-token");
        assertThat(idleService.issueBrowserSession(idleIssue)).isTrue();
        String idleTicket = cookieValue(idleIssue.headerOfResponse("Set-Cookie"));

        now.addAndGet(30L * 60L * 1000L);
        assertThat(idleService.authenticationMethod(sessionContext(idleTicket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.NONE);

        now.set(20_000L);
        DashboardAuthService absoluteService =
                new DashboardAuthService(config, new SecureRandom(), now::get);
        ContextEmpty absoluteIssue = bearerContext("timeout-token");
        assertThat(absoluteService.issueBrowserSession(absoluteIssue)).isTrue();
        String absoluteTicket = cookieValue(absoluteIssue.headerOfResponse("Set-Cookie"));
        for (int interval = 1; interval <= 16; interval++) {
            now.set(20_000L + interval * 29L * 60L * 1000L);
            assertThat(absoluteService.authenticationMethod(sessionContext(absoluteTicket)))
                    .isEqualTo(DashboardAuthService.AuthenticationMethod.SESSION);
        }
        now.set(20_000L + 8L * 60L * 60L * 1000L);
        assertThat(absoluteService.authenticationMethod(sessionContext(absoluteTicket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.NONE);
    }

    /** 验证会话容量达到 1024 后淘汰最久未使用的记录。 */
    @Test
    void shouldEvictLeastRecentlyUsedSessionAtCapacity() {
        AppConfig config = config("capacity-token");
        DashboardAuthService service = new DashboardAuthService(config);
        String firstTicket = "";
        String latestTicket = "";
        for (int index = 0; index < 1025; index++) {
            ContextEmpty issue = bearerContext("capacity-token");
            assertThat(service.issueBrowserSession(issue)).isTrue();
            String ticket = cookieValue(issue.headerOfResponse("Set-Cookie"));
            if (index == 0) {
                firstTicket = ticket;
            }
            latestTicket = ticket;
        }

        assertThat(service.authenticationMethod(sessionContext(firstTicket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.NONE);
        assertThat(service.authenticationMethod(sessionContext(latestTicket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.SESSION);
    }

    /** 验证显式退出同时撤销服务端短会话并删除同路径 Cookie。 */
    @Test
    void shouldRevokeServerSessionAndClearCookie() {
        AppConfig config = config("logout-token");
        DashboardAuthService service = new DashboardAuthService(config);
        ContextEmpty issue = bearerContext("logout-token");
        assertThat(service.issueBrowserSession(issue)).isTrue();
        String ticket = cookieValue(issue.headerOfResponse("Set-Cookie"));
        ContextEmpty logout = sessionContext(ticket);

        service.revokeBrowserSession(logout);

        assertThat(logout.headerOfResponse("Set-Cookie"))
                .contains("Max-Age=0")
                .contains("Path=/api")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
        assertThat(service.authenticationMethod(sessionContext(ticket)))
                .isEqualTo(DashboardAuthService.AuthenticationMethod.NONE);
    }

    /**
     * 创建带固定长期令牌的应用配置。
     *
     * @param token Dashboard 长期令牌。
     * @return 测试应用配置。
     */
    private AppConfig config(String token) {
        AppConfig config = new AppConfig();
        config.getDashboard().setAccessToken(token);
        return config;
    }

    /**
     * 创建带长期 Bearer 的测试上下文。
     *
     * @param token Dashboard 长期令牌。
     * @return 带 Authorization 头的上下文。
     */
    private ContextEmpty bearerContext(String token) {
        ContextEmpty context = new ContextEmpty();
        context.headerMap().put("Authorization", "Bearer " + token);
        return context;
    }

    /**
     * 创建带短会话 Cookie 的测试上下文。
     *
     * @param ticket Dashboard 短会话票据。
     * @return 带短会话 Cookie 的上下文。
     */
    private ContextEmpty sessionContext(String ticket) {
        ContextEmpty context = new ContextEmpty();
        context.cookieMap().put(DashboardAuthService.SESSION_COOKIE_NAME, ticket);
        return context;
    }

    /**
     * 从 Set-Cookie 响应头提取第一段 Cookie 值。
     *
     * @param setCookie Set-Cookie 响应头。
     * @return Cookie 票据。
     */
    private String cookieValue(String setCookie) {
        String first = setCookie == null ? "" : setCookie.split(";", 2)[0];
        int separator = first.indexOf('=');
        return separator < 0 ? "" : first.substring(separator + 1);
    }
}
