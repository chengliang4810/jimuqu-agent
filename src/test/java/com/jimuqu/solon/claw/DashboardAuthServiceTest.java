package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.web.DashboardAuthService;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextEmpty;

public class DashboardAuthServiceTest {
    @Test
    void shouldRejectBlankAccessTokenInsteadOfAdminFallback() {
        AppConfig config = new AppConfig();
        config.getDashboard().setAccessToken("");

        DashboardAuthService authService = new DashboardAuthService(config);

        assertThat(authService.sessionToken()).isEmpty();
        assertThat(authService.isAuthorized(new HeaderContext("Bearer admin"))).isFalse();
    }

    @Test
    void shouldDetectExplicitAdminAsWeakDefaultToken() {
        AppConfig config = new AppConfig();
        config.getDashboard().setAccessToken("admin");

        DashboardAuthService authService = new DashboardAuthService(config);

        assertThat(authService.sessionToken()).isEqualTo("admin");
        assertThat(authService.hasWeakDefaultToken()).isTrue();
    }

    @Test
    void shouldAcceptBearerSchemeCaseInsensitively() {
        AppConfig config = new AppConfig();
        config.getDashboard().setAccessToken("test-token");

        DashboardAuthService authService = new DashboardAuthService(config);

        assertThat(authService.isAuthorized(new HeaderContext("bearer test-token"))).isTrue();
        assertThat(authService.isAuthorized(new HeaderContext("BEARER test-token"))).isTrue();
        assertThat(authService.isAuthorized(new HeaderContext("Bearer wrong-token"))).isFalse();
        assertThat(authService.isAuthorized(new HeaderContext("Bearer TEST-TOKEN"))).isFalse();
        assertThat(authService.isAuthorized(new HeaderContext("Bearer test-token-suffix")))
                .isFalse();
    }

    /** 验证通配监听地址不会授权任意浏览器 Origin。 */
    @Test
    void shouldNotTreatWildcardBindHostAsAllowedOrigin() {
        AppConfig config = new AppConfig();
        config.getDashboard().setBindHost("0.0.0.0");
        config.getDashboard().setBindPort(8080);
        DashboardAuthService authService = new DashboardAuthService(config);

        assertThat(
                        authService.isAllowedDashboardOrigin(
                                requestContext("http", "127.0.0.1:8080"),
                                "http://evil.example.com:8080"))
                .isFalse();
    }

    /** 验证解析结果同为 loopback 的不同主机也不能绕过精确同源比较。 */
    @Test
    void shouldNotTrustDifferentLoopbackOrigin() {
        DashboardAuthService authService = new DashboardAuthService(new AppConfig());

        assertThat(
                        authService.isAllowedDashboardOrigin(
                                requestContext("http", "127.0.0.1:8080"), "http://127.0.0.2:8080"))
                .isFalse();
    }

    /** 验证 Origin 必须与实际请求入口的协议、主机和有效端口全部一致。 */
    @Test
    void shouldRequireExactRequestOrigin() {
        DashboardAuthService authService = new DashboardAuthService(new AppConfig());
        ContextEmpty context = requestContext("https", "dashboard.example.com");

        assertThat(authService.isAllowedDashboardOrigin(context, "https://dashboard.example.com"))
                .isTrue();
        assertThat(authService.isAllowedDashboardOrigin(context, "http://dashboard.example.com"))
                .isFalse();
        assertThat(
                        authService.isAllowedDashboardOrigin(
                                context, "https://dashboard.example.com:444"))
                .isFalse();
        assertThat(
                        authService.isAllowedDashboardOrigin(
                                context, "https://user@dashboard.example.com"))
                .isFalse();
        assertThat(
                        authService.isAllowedDashboardOrigin(
                                context, "https://dashboard.example.com/path"))
                .isFalse();
    }

    /** 创建带请求入口协议和 Host 的测试上下文。 */
    private ContextEmpty requestContext(String scheme, String host) {
        ContextEmpty context = new ContextEmpty();
        context.headerMap().put("X-Forwarded-Proto", scheme);
        context.headerMap().put("Host", host);
        return context;
    }

    /** 测试用上下文，只暴露 Dashboard 鉴权需要读取的 Authorization 头。 */
    private static class HeaderContext extends ContextEmpty {
        /**
         * 创建带 Authorization 头的请求上下文。
         *
         * @param authorization Authorization 头内容。
         */
        private HeaderContext(String authorization) {
            headerMap().put("Authorization", authorization);
        }
    }
}
