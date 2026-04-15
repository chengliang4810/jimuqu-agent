package com.jimuqu.agent;

import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.core.SessionRecord;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayCommandFlowTest {
    @Test
    void shouldHandleBasicCommandsAndConversationFlow() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        GatewayReply firstReply = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "room-1", "user-1", "hello"));
        assertThat(firstReply.getContent()).contains("echo:hello");
        String firstSessionId = firstReply.getSessionId();

        GatewayReply statusReply = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "room-1", "user-1", "/status"));
        assertThat(statusReply.getContent()).contains(firstSessionId);

        GatewayReply retryReply = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "room-1", "user-1", "/retry"));
        assertThat(retryReply.getContent()).contains("echo:hello");

        GatewayReply branchReply = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "room-1", "user-1", "/branch review"));
        assertThat(branchReply.getContent()).contains("review");

        GatewayReply undoReply = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "room-1", "user-1", "/undo"));
        assertThat(undoReply.getContent()).contains("Removed");

        GatewayReply newReply = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "room-1", "user-1", "/new"));
        assertThat(newReply.getSessionId()).isNotEqualTo(firstSessionId);

        SessionRecord rebound = env.sessionRepository.getBoundSession("MEMORY:room-1:user-1");
        assertThat(rebound.getSessionId()).isEqualTo(newReply.getSessionId());
    }
}
