package com.jimuqu.claw.channel;

import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.channel.http.ChannelWebhookController;
import com.jimuqu.claw.channel.http.RuntimeDebugController;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;
import com.jimuqu.claw.config.ClawProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Import;
import org.noear.solon.test.HttpTester;
import org.noear.solon.test.SolonTest;

import java.time.Instant;

@SolonTest(value = ChannelIngressTest.TestApp.class, args = "--server.port=7076", enableHttp = true)
public class ChannelIngressTest extends HttpTester {
    private static final CapturingRuntimeService CAPTURING_RUNTIME_SERVICE = new CapturingRuntimeService();

    private CapturingRuntimeService runtimeService;

    @BeforeEach
    public void reset() {
        runtimeService().lastInbound = null;
        runtimeService().lastRequest = null;
    }

    @AfterAll
    public static void afterAll() {
        if (Solon.app() != null) {
            Solon.stopBlock();
        }
    }

    @Test
    public void runtimeDebugEndpointBuildsRunRequest() throws Exception {
        String response = path("/api/runtime/run")
                .bodyOfJson("{"
                        + "\"platform\":\"wecom\","
                        + "\"chat_id\":\"chat-1\","
                        + "\"thread_id\":\"thread-1\","
                        + "\"user_id\":\"user-1\","
                        + "\"text\":\"debug hello\","
                        + "\"reply_target\":\"wecom:chat-1:thread-1\","
                        + "\"skills\":[\"alpha\",\"beta\"]"
                        + "}")
                .post();

        ONode json = ONode.ofJson(response);
        Assertions.assertTrue(json.get("success").getBoolean());
        Assertions.assertEquals("run-debug", json.get("run_id").getString());

        Assertions.assertNotNull(runtimeService().lastRequest);
        Assertions.assertEquals("wecom", runtimeService().lastRequest.getSessionContext().getPlatform());
        Assertions.assertEquals("chat-1", runtimeService().lastRequest.getSessionContext().getChatId());
        Assertions.assertEquals("thread-1", runtimeService().lastRequest.getSessionContext().getThreadId());
        Assertions.assertEquals("user-1", runtimeService().lastRequest.getSessionContext().getUserId());
        Assertions.assertEquals("debug hello", runtimeService().lastRequest.getUserMessage());
        Assertions.assertEquals("wecom:chat-1:thread-1", ReplyRouteSupport.format(runtimeService().lastRequest.getReplyRoute()));
        Assertions.assertEquals(2, runtimeService().lastRequest.getSkillNames().size());
    }

    @Test
    public void webhookEndpointParsesInboundAndInvokesRuntime() throws Exception {
        String response = path("/api/channels/feishu/inbound")
                .bodyOfJson("{"
                        + "\"message_id\":\"msg-1\","
                        + "\"text\":\"inbound hello\","
                        + "\"chat_id\":\"chat-fei\","
                        + "\"thread_id\":\"thread-fei\","
                        + "\"user_id\":\"user-fei\""
                        + "}")
                .post();

        ONode json = ONode.ofJson(response);
        Assertions.assertTrue(json.get("success").getBoolean());
        Assertions.assertEquals("run-inbound", json.get("run_id").getString());

        Assertions.assertNotNull(runtimeService().lastInbound);
        Assertions.assertEquals("feishu", runtimeService().lastInbound.getSessionContext().getPlatform());
        Assertions.assertEquals("chat-fei", runtimeService().lastInbound.getSessionContext().getChatId());
        Assertions.assertEquals("thread-fei", runtimeService().lastInbound.getSessionContext().getThreadId());
        Assertions.assertEquals("user-fei", runtimeService().lastInbound.getSessionContext().getUserId());
        Assertions.assertEquals("inbound hello", runtimeService().lastInbound.getText());
        Assertions.assertEquals("feishu:chat-fei:thread-fei", ReplyRouteSupport.format(runtimeService().lastInbound.getReplyRoute()));
    }

    @Test
    public void feishuAdapterSupportsJsonStringContent() {
        ClawProperties.ChannelProperties properties = new ClawProperties.ChannelProperties();
        properties.setEnabled(Boolean.TRUE);

        FeishuChannelAdapter adapter = new FeishuChannelAdapter(properties);
        ChannelInboundMessage inboundMessage = adapter.parseInbound("{"
                + "\"event\":{"
                + "\"message\":{"
                + "\"message_id\":\"msg-fei\","
                + "\"chat_id\":\"chat-fei\","
                + "\"thread_id\":\"thread-fei\","
                + "\"content\":\"{\\\"text\\\":\\\"rich hello\\\"}\""
                + "},"
                + "\"sender\":{"
                + "\"sender_id\":{"
                + "\"open_id\":\"open-user\""
                + "}"
                + "}"
                + "}"
                + "}");

        Assertions.assertEquals("msg-fei", inboundMessage.getMessageId());
        Assertions.assertEquals("rich hello", inboundMessage.getText());
        Assertions.assertEquals("chat-fei", inboundMessage.getSessionContext().getChatId());
        Assertions.assertEquals("thread-fei", inboundMessage.getSessionContext().getThreadId());
        Assertions.assertEquals("open-user", inboundMessage.getSessionContext().getUserId());
    }

    @Import(classes = {RuntimeDebugController.class, ChannelWebhookController.class, TestConfig.class})
    public static class TestApp {
        public static void main(String[] args) {
            Solon.start(TestApp.class, args);
        }
    }

    @Configuration
    public static class TestConfig {
        @Bean
        public CapturingRuntimeService capturingRuntimeService() {
            return CAPTURING_RUNTIME_SERVICE;
        }

        @Bean
        public RuntimeService runtimeService(CapturingRuntimeService runtimeService) {
            return runtimeService;
        }

        @Bean
        public ChannelAdapter feishuChannelAdapter() {
            ClawProperties.ChannelProperties properties = new ClawProperties.ChannelProperties();
            properties.setEnabled(Boolean.TRUE);
            return new FeishuChannelAdapter(properties);
        }
    }

    public static class CapturingRuntimeService implements RuntimeService {
        private RunRequest lastRequest;
        private ChannelInboundMessage lastInbound;

        @Override
        public RunRecord handleInbound(ChannelInboundMessage inboundMessage) {
            this.lastInbound = inboundMessage;
            return RunRecord.builder()
                    .runId("run-inbound")
                    .sessionId("sess-inbound")
                    .status(RunStatus.SUCCEEDED)
                    .responseText("inbound-ok")
                    .createdAt(Instant.now())
                    .build();
        }

        @Override
        public RunRecord handleRequest(RunRequest request) {
            this.lastRequest = request;
            return RunRecord.builder()
                    .runId("run-debug")
                    .parentRunId(request.getParentRunId())
                    .sessionId("sess-debug")
                    .status(RunStatus.SUCCEEDED)
                    .responseText("debug-ok")
                    .createdAt(Instant.now())
                    .build();
        }
    }

    private CapturingRuntimeService runtimeService() {
        if (runtimeService == null) {
            runtimeService = CAPTURING_RUNTIME_SERVICE;
        }
        return runtimeService;
    }
}
