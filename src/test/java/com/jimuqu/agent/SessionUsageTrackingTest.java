package com.jimuqu.agent;

import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.support.TestEnvironment;
import com.jimuqu.agent.web.DashboardAnalyticsService;
import com.jimuqu.agent.web.DashboardSessionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionUsageTrackingTest {
    @Test
    void shouldTrackUsageForSessionCommandAndDashboard() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        bootstrapAdmin(env);

        GatewayReply conversation = env.send("usage-chat", "usage-user", "请总结一下 token 使用情况");
        assertThat(conversation.getContent()).contains("echo:");

        SessionRecord session = env.sessionRepository.getBoundSession("MEMORY:usage-chat:usage-user");
        assertThat(session.getLastInputTokens()).isGreaterThan(0L);
        assertThat(session.getLastOutputTokens()).isGreaterThan(0L);
        assertThat(session.getLastTotalTokens()).isEqualTo(session.getLastInputTokens() + session.getLastOutputTokens());
        assertThat(session.getCumulativeTotalTokens()).isEqualTo(session.getLastTotalTokens());
        assertThat(session.getLastResolvedModel()).isEqualTo("gpt-5.4");

        GatewayReply usageReply = env.send("usage-chat", "usage-user", "/usage");
        assertThat(usageReply.getContent()).contains("cumulative_total_tokens=").contains("last_total_tokens=");

        DashboardSessionService sessionService = new DashboardSessionService(env.sessionRepository);
        Map<String, Object> sessions = sessionService.getSessions(10, 0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) sessions.get("sessions");
        assertThat(items).isNotEmpty();
        Map<String, Object> first = items.get(0);
        assertThat(((Number) first.get("total_tokens")).longValue()).isGreaterThan(0L);
        assertThat(String.valueOf(first.get("model"))).isEqualTo("gpt-5.4");

        Map<String, Object> messageDetails = sessionService.getSessionMessages(session.getSessionId());
        assertThat(((Number) messageDetails.get("total_tokens")).longValue()).isGreaterThan(0L);
        assertThat(((Number) messageDetails.get("last_total_tokens")).longValue()).isGreaterThan(0L);

        DashboardAnalyticsService analyticsService = new DashboardAnalyticsService(env.sessionRepository);
        Map<String, Object> analytics = analyticsService.getUsage(30);
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) analytics.get("totals");
        assertThat(((Number) totals.get("total_input")).longValue()).isGreaterThan(0L);
        assertThat(((Number) totals.get("total_output")).longValue()).isGreaterThan(0L);
        assertThat(((Number) totals.get("total_sessions")).intValue()).isGreaterThanOrEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> daily = (List<Map<String, Object>>) analytics.get("daily");
        assertThat(daily)
                .isNotEmpty()
                .anySatisfy(day -> {
                    long tokens = ((Number) day.get("input_tokens")).longValue()
                            + ((Number) day.get("output_tokens")).longValue();
                    assertThat(tokens).isGreaterThan(0L);
                    assertThat(((Number) day.get("sessions")).intValue()).isGreaterThanOrEqualTo(1);
                });
    }

    private void bootstrapAdmin(TestEnvironment env) throws Exception {
        env.send("usage-chat", "usage-user", "hello");
        env.send("usage-chat", "usage-user", "/pairing claim-admin");
    }
}
