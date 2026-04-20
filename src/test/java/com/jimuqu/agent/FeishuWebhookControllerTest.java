package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.service.ChannelAdapter;
import com.jimuqu.agent.core.service.InboundMessageHandler;
import com.jimuqu.agent.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.agent.support.AttachmentCacheService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class FeishuWebhookControllerTest {
    @Test
    void shouldReturnChallengeForUrlVerification() {
        FeishuChannelAdapter adapter = adapter();

        String response = adapter.handleWebhook("{\"type\":\"url_verification\",\"challenge\":\"abc123\"}");

        assertThat(response).contains("\"challenge\":\"abc123\"");
    }

    @Test
    void shouldConvertInboundTextWebhookToGatewayMessage() {
        FeishuChannelAdapter adapter = adapter();
        final AtomicReference<GatewayMessage> captured = new AtomicReference<GatewayMessage>();
        adapter.setInboundMessageHandler(new InboundMessageHandler() {
            @Override
            public void handle(GatewayMessage message) {
                captured.set(message);
            }
        });

        String body = "{"
                + "\"header\":{\"event_type\":\"im.message.receive_v1\"},"
                + "\"event\":{"
                + "\"message\":{\"message_id\":\"om_1\",\"chat_id\":\"oc_chat\",\"chat_type\":\"p2p\",\"message_type\":\"text\",\"content\":\"{\\\"text\\\":\\\"hello feishu\\\"}\"},"
                + "\"sender\":{\"sender_id\":{\"open_id\":\"ou_user\"}}"
                + "}"
                + "}";

        String response = adapter.handleWebhook(body);

        assertThat(response).contains("\"code\":0");
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getPlatform()).isEqualTo(PlatformType.FEISHU);
        assertThat(captured.get().getChatId()).isEqualTo("oc_chat");
        assertThat(captured.get().getUserId()).isEqualTo("ou_user");
        assertThat(captured.get().getText()).isEqualTo("hello feishu");
    }

    private FeishuChannelAdapter adapter() {
        AppConfig.ChannelConfig channelConfig = new AppConfig.ChannelConfig();
        channelConfig.setEnabled(true);
        channelConfig.setAppId("app");
        channelConfig.setAppSecret("secret");
        return new FeishuChannelAdapter(channelConfig, new AttachmentCacheService(new AppConfig())) {
            @Override
            public boolean connect() {
                return true;
            }
        };
    }
}
