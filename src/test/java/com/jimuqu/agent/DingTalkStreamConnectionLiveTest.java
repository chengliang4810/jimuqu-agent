package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DingTalkStreamConnectionLiveTest {
    @Test
    void shouldConnectToDingTalkStreamMode() {
        String clientId = System.getenv("JIMUQU_DINGTALK_CLIENT_ID");
        String clientSecret = System.getenv("JIMUQU_DINGTALK_CLIENT_SECRET");
        String robotCode = System.getenv("JIMUQU_DINGTALK_ROBOT_CODE");

        Assumptions.assumeTrue(clientId != null && clientId.trim().length() > 0);
        Assumptions.assumeTrue(clientSecret != null && clientSecret.trim().length() > 0);
        Assumptions.assumeTrue(robotCode != null && robotCode.trim().length() > 0);

        AppConfig.ChannelConfig config = new AppConfig.ChannelConfig();
        config.setEnabled(true);
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRobotCode(robotCode);

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(config);
        boolean connected = adapter.connect();
        try {
            assertThat(connected).isTrue();
            assertThat(adapter.isConnected()).isTrue();
            assertThat(adapter.detail()).contains("connected");
        } finally {
            adapter.disconnect();
        }
    }
}
