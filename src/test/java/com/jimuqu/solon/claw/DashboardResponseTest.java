package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.web.DashboardResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.ContextEmpty;

/** 验证控制台响应辅助类在设置 HTTP 状态和错误脱敏时保持统一契约。 */
public class DashboardResponseTest {
    /** 应在构造错误响应时同步设置状态码，并继续沿用已有敏感文本脱敏策略。 */
    @Test
    void shouldSetHttpStatusWhenBuildingDashboardError() {
        Context context = ContextEmpty.create();

        Map<String, Object> response =
                DashboardResponse.error(
                        context, 418, "DASHBOARD_TEST_ERROR", "token=sk-dashboardresponse12345");

        assertThat(context.status()).isEqualTo(418);
        assertThat(response.get("success")).isEqualTo(Boolean.FALSE);
        assertThat(response.get("code")).isEqualTo("DASHBOARD_TEST_ERROR");
        assertThat(String.valueOf(response.get("error"))).contains("token=***");
        assertThat(String.valueOf(response)).doesNotContain("sk-dashboardresponse12345");
    }

    /** 服务端异常应返回固定公共消息，不得暴露路径、SQL、令牌或异常类型。 */
    @Test
    void shouldHideInternalExceptionDetailsFromServerErrors() {
        Context context = ContextEmpty.create();
        String sensitive =
                "SQLException at /srv/solonclaw/config.yml token=sk-dashboardresponse67890";

        Map<String, Object> response =
                DashboardResponse.error(
                        context,
                        500,
                        "DASHBOARD_INTERNAL_ERROR",
                        new IllegalStateException(sensitive));

        assertThat(context.status()).isEqualTo(500);
        assertThat(response.get("code")).isEqualTo("DASHBOARD_INTERNAL_ERROR");
        assertThat(response.get("error")).isEqualTo("请求处理失败 / Request failed");
        assertThat(String.valueOf(response))
                .doesNotContain("/srv/solonclaw/config.yml")
                .doesNotContain("sk-dashboardresponse67890")
                .doesNotContain("SQLException")
                .doesNotContain("IllegalStateException");
    }

    /** 受控的客户端错误应继续返回原业务消息，并沿用敏感文本脱敏。 */
    @Test
    void shouldPreserveControlledClientErrorMessages() {
        Context context = ContextEmpty.create();

        Map<String, Object> response =
                DashboardResponse.error(
                        context,
                        400,
                        "DASHBOARD_BAD_REQUEST",
                        new IllegalArgumentException("参数无效 token=sk-controlled12345"));

        assertThat(context.status()).isEqualTo(400);
        assertThat(response.get("code")).isEqualTo("DASHBOARD_BAD_REQUEST");
        assertThat(String.valueOf(response.get("error")))
                .contains("参数无效")
                .contains("token=***")
                .doesNotContain("sk-controlled12345");
    }
}
