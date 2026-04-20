package com.jimuqu.agent;

import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.service.CommandService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.gateway.service.DefaultGatewayService;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayErrorHandlingTest {
    @Test
    void shouldReturnFriendlyInterruptedMessage() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.send("chat-a", "user-a", "hello");
        env.send("chat-a", "user-a", "/pairing claim-admin");

        DefaultGatewayService gatewayService = new DefaultGatewayService(
                new NoopCommandService(),
                new InterruptedConversationOrchestrator(),
                env.deliveryService,
                env.sessionRepository,
                env.gatewayAuthorizationService,
                new NoopSkillLearningService(),
                env.memoryManager
        );

        GatewayMessage message = env.message("chat-a", "user-a", "改为gpt-5.4吧");
        GatewayReply reply = gatewayService.handle(message);

        assertThat(reply).isNotNull();
        assertThat(reply.isError()).isTrue();
        assertThat(reply.getContent()).isEqualTo("处理消息失败：当前操作被中断，请重试一次。");
        assertThat(env.memoryChannelAdapter.getLastRequest()).isNotNull();
        assertThat(env.memoryChannelAdapter.getLastRequest().getText()).isEqualTo("处理消息失败：当前操作被中断，请重试一次。");
    }

    private static class NoopCommandService implements CommandService {
        @Override
        public boolean supports(String commandName) {
            return false;
        }

        @Override
        public GatewayReply handle(GatewayMessage message, String commandLine) {
            return null;
        }
    }

    private static class InterruptedConversationOrchestrator implements ConversationOrchestrator {
        @Override
        public GatewayReply handleIncoming(GatewayMessage message) throws Exception {
            throw new InterruptedException();
        }

        @Override
        public GatewayReply runScheduled(GatewayMessage syntheticMessage) throws Exception {
            throw new InterruptedException();
        }
    }

    private static class NoopSkillLearningService implements SkillLearningService {
        @Override
        public void schedulePostReplyLearning(SessionRecord session, GatewayMessage message, GatewayReply reply) {
        }
    }
}
