package com.jimuqu.solon.claw.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.SensitiveConfigAuditEvent;
import com.jimuqu.solon.claw.core.repository.SensitiveConfigAuditRepository;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.io.File;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextEmpty;

/** Dashboard 敏感配置审计映射与失败关闭测试。 */
class DashboardSensitiveConfigAuditServiceTest {
    /** Bearer 审计只能记录服务端观察到的连接 IP，不能信任转发头。 */
    @Test
    void shouldRecordBearerMetadataWithoutTrustingForwardedIp() {
        com.jimuqu.solon.claw.config.AppConfig config =
                new com.jimuqu.solon.claw.config.AppConfig();
        config.getDashboard().setAccessToken("audit-bearer-token");
        DashboardAuthService authService = new DashboardAuthService(config);
        AtomicReference<SensitiveConfigAuditEvent> captured =
                new AtomicReference<SensitiveConfigAuditEvent>();
        DashboardSensitiveConfigAuditService service =
                new DashboardSensitiveConfigAuditService(captured::set, authService);
        AuditContext context =
                new AuditContext("203.0.113.9", "", "Bearer audit-bearer-token", "198.51.100.77");

        String requestId =
                service.recordSecretReveal(context, "solonclaw.gateway.injectionSecret", "Worker");

        SensitiveConfigAuditEvent event = captured.get();
        assertThat(event.getEventId()).isEqualTo(requestId);
        assertThat(event.getOperation())
                .isEqualTo(DashboardSensitiveConfigAuditService.OPERATION_SECRET_REVEAL);
        assertThat(event.getActorType())
                .isEqualTo(DashboardSensitiveConfigAuditService.ACTOR_DASHBOARD);
        assertThat(event.getAuthMethod()).isEqualTo("BEARER");
        assertThat(event.getProfile()).isEqualTo("worker");
        assertThat(event.getConfigKey()).isEqualTo("solonclaw.gateway.injectionSecret");
        assertThat(event.getRemoteIp()).isEqualTo("203.0.113.9");
        assertThat(event.getCreatedAt()).isPositive();
    }

    /** 短会话认证的审计方式必须记录为 SESSION，不能退化为匿名主体。 */
    @Test
    void shouldRecordSessionAuthenticationMethod() {
        com.jimuqu.solon.claw.config.AppConfig config =
                new com.jimuqu.solon.claw.config.AppConfig();
        config.getDashboard().setAccessToken("audit-session-token");
        DashboardAuthService authService = new DashboardAuthService(config);
        ContextEmpty issue = new ContextEmpty();
        issue.headerMap().put("Authorization", "Bearer audit-session-token");
        assertThat(authService.issueBrowserSession(issue)).isTrue();
        String ticket = cookieValue(issue.headerOfResponse("Set-Cookie"));
        AtomicReference<SensitiveConfigAuditEvent> captured =
                new AtomicReference<SensitiveConfigAuditEvent>();
        DashboardSensitiveConfigAuditService service =
                new DashboardSensitiveConfigAuditService(captured::set, authService);
        AuditContext context = new AuditContext("127.0.0.1", "", null, null);
        context.cookieMap().put(DashboardAuthService.SESSION_COOKIE_NAME, ticket);

        service.recordSecretSetAttempt(context, "solonclaw.dashboard.accessToken", "default");

        assertThat(captured.get().getAuthMethod()).isEqualTo("SESSION");
    }

    /** 审计存储失败时，密钥写入必须返回 503 且配置文件保持未修改。 */
    @Test
    void shouldNotMutateSecretWhenSetAuditIsUnavailable() throws Exception {
        TestEnvironment environment = TestEnvironment.withFakeLlm();
        environment.appConfig.getDashboard().setAccessToken("audit-controller-token");
        DashboardRuntimeConfigService runtimeConfigService =
                new DashboardRuntimeConfigService(
                        environment.appConfig, environment.gatewayRuntimeRefreshService);
        DashboardAuthService authService = new DashboardAuthService(environment.appConfig);
        DashboardRuntimeConfigController controller =
                controller(runtimeConfigService, authService, failingRepository());
        String secret = "audit-write-must-not-persist";
        File configFile = new File(environment.appConfig.getRuntime().getConfigFile());
        String before =
                configFile.exists() ? cn.hutool.core.io.FileUtil.readUtf8String(configFile) : "";
        AuditContext context =
                new AuditContext(
                        "127.0.0.1",
                        "{\"key\":\"solonclaw.gateway.injectionSecret\",\"value\":\""
                                + secret
                                + "\"}",
                        "Bearer audit-controller-token",
                        null);

        Map<String, Object> response = controller.set(context);

        assertAuditUnavailable(context, response);
        String after =
                configFile.exists() ? cn.hutool.core.io.FileUtil.readUtf8String(configFile) : "";
        assertThat(after).isEqualTo(before).doesNotContain(secret);
    }

    /** 审计存储失败时，reveal 已读取的局部明文也绝不能进入 HTTP 响应。 */
    @Test
    void shouldNotReturnRevealedSecretWhenAuditIsUnavailable() throws Exception {
        TestEnvironment environment = TestEnvironment.withFakeLlm();
        environment.appConfig.getDashboard().setAccessToken("audit-controller-token");
        DashboardRuntimeConfigService runtimeConfigService =
                new DashboardRuntimeConfigService(
                        environment.appConfig, environment.gatewayRuntimeRefreshService);
        String secret = "audit-reveal-must-not-leak";
        runtimeConfigService.set("solonclaw.gateway.injectionSecret", secret);
        DashboardAuthService authService = new DashboardAuthService(environment.appConfig);
        DashboardRuntimeConfigController controller =
                controller(runtimeConfigService, authService, failingRepository());
        AuditContext context =
                new AuditContext(
                        "127.0.0.1",
                        "{\"key\":\"solonclaw.gateway.injectionSecret\"}",
                        "Bearer audit-controller-token",
                        null);

        Map<String, Object> response = controller.reveal(context);

        assertAuditUnavailable(context, response);
        assertThat(response).doesNotContainKey("data").doesNotContainValue(secret);
        assertThat(String.valueOf(response)).doesNotContain(secret);
    }

    /** 创建带指定审计仓储的控制器。 */
    private DashboardRuntimeConfigController controller(
            DashboardRuntimeConfigService runtimeConfigService,
            DashboardAuthService authService,
            SensitiveConfigAuditRepository repository) {
        return new DashboardRuntimeConfigController(
                runtimeConfigService,
                authService,
                new DashboardSensitiveConfigAuditService(repository, authService));
    }

    /** 创建始终模拟审计存储故障的仓储。 */
    private SensitiveConfigAuditRepository failingRepository() {
        return event -> {
            throw new IllegalStateException("simulated audit outage with secret=must-not-leak");
        };
    }

    /** 断言审计故障使用固定 503 契约且不暴露底层异常。 */
    private void assertAuditUnavailable(AuditContext context, Map<String, Object> response) {
        assertThat(context.status()).isEqualTo(503);
        assertThat(context.headerOfResponse("X-Request-Id")).isNotBlank();
        assertThat(response)
                .containsEntry("success", false)
                .containsEntry("code", "WORKSPACE_CONFIG_AUDIT_UNAVAILABLE")
                .containsEntry("error", "请求处理失败 / Request failed")
                .doesNotContainKey("data");
        assertThat(String.valueOf(response))
                .doesNotContain("simulated audit outage")
                .doesNotContain("must-not-leak");
    }

    /** 从 Set-Cookie 响应头提取短会话票据。 */
    private String cookieValue(String setCookie) {
        String first = setCookie == null ? "" : setCookie.split(";", 2)[0];
        int separator = first.indexOf('=');
        return separator < 0 ? "" : first.substring(separator + 1);
    }

    /** 测试用 HTTP 上下文，显式区分直连 IP 与不可信转发头。 */
    private static final class AuditContext extends ContextEmpty {
        /** 服务端直接观察到的远端 IP。 */
        private final String remoteIp;

        /** JSON 请求体。 */
        private final String body;

        /**
         * 创建审计测试请求上下文。
         *
         * @param remoteIp 服务端直接观察到的远端 IP。
         * @param body JSON 请求体。
         * @param authorization Authorization 请求头。
         * @param forwardedFor 不可信的 X-Forwarded-For 请求头。
         */
        private AuditContext(
                String remoteIp, String body, String authorization, String forwardedFor) {
            this.remoteIp = remoteIp;
            this.body = body;
            if (authorization != null) {
                headerMap().put("Authorization", authorization);
            }
            if (forwardedFor != null) {
                headerMap().put("X-Forwarded-For", forwardedFor);
            }
        }

        /** 返回服务端直接观察到的远端 IP。 */
        @Override
        public String remoteIp() {
            return remoteIp;
        }

        /** 返回测试 JSON 请求体。 */
        @Override
        public String body() {
            return body;
        }
    }
}
