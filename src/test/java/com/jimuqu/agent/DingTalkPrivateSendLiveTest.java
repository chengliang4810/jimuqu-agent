package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DingTalkPrivateSendLiveTest {
    @Test
    void shouldTryPrivateChatSend() throws Exception {
        String clientId = System.getenv("JIMUQU_DINGTALK_CLIENT_ID");
        String clientSecret = System.getenv("JIMUQU_DINGTALK_CLIENT_SECRET");
        String robotCode = System.getenv("JIMUQU_DINGTALK_ROBOT_CODE");
        String openConversationId = System.getenv("JIMUQU_DINGTALK_PRIVATE_OPEN_CONVERSATION_ID");
        String userId = System.getenv("JIMUQU_DINGTALK_PRIVATE_USER_ID");

        Assumptions.assumeTrue(clientId != null && clientId.trim().length() > 0);
        Assumptions.assumeTrue(clientSecret != null && clientSecret.trim().length() > 0);
        Assumptions.assumeTrue(robotCode != null && robotCode.trim().length() > 0);
        Assumptions.assumeTrue(openConversationId != null && openConversationId.trim().length() > 0);
        Assumptions.assumeTrue(userId != null && userId.trim().length() > 0);

        AppConfig.ChannelConfig config = new AppConfig.ChannelConfig();
        config.setEnabled(true);
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRobotCode(robotCode);

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(config);
        adapter.connect();
        try {
            Field field = DingTalkChannelAdapter.class.getDeclaredField("conversationGroupFlags");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Boolean> flags = (Map<String, Boolean>) field.get(adapter);
            flags.put(openConversationId, Boolean.FALSE);
            adapter.send(new DeliveryRequest(PlatformType.DINGTALK, openConversationId, userId, "dm", null, "私聊发送测试"));
            assertThat(adapter.isConnected()).isTrue();
        } finally {
            adapter.disconnect();
        }
    }
}

