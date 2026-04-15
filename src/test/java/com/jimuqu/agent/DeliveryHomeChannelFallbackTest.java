package com.jimuqu.agent;

import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryHomeChannelFallbackTest {
    @Test
    void shouldUseHomeChannelWhenChatIdIsEmpty() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.send("admin-dm", "admin-user", "hello");
        env.send("admin-dm", "admin-user", "/pairing claim-admin");
        env.gatewayService.handle(env.message("group-1", "admin-user", "group", "Dev Group", "Alice", "/sethome"));

        env.deliveryService.deliver(new DeliveryRequest(PlatformType.MEMORY, null, null, null, "scheduled"));
        assertThat(env.memoryChannelAdapter.getLastRequest().getChatId()).isEqualTo("group-1");
        assertThat(env.memoryChannelAdapter.getLastRequest().getText()).isEqualTo("scheduled");
    }
}
