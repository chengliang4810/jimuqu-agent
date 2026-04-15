package com.jimuqu.agent;

import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.core.HomeChannelRecord;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HomeChannelCommandTest {
    @Test
    void shouldAllowOnlyAdminToSetHomeChannel() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.send("admin-dm", "admin-user", "hello");
        env.send("admin-dm", "admin-user", "/pairing claim-admin");

        GatewayReply denied = env.gatewayService.handle(env.message("group-1", "user-b", "group", "Dev Group", "Bob", "/sethome"));
        assertThat(denied).isNull();

        GatewayReply success = env.gatewayService.handle(env.message("group-1", "admin-user", "group", "Dev Group", "Alice", "/sethome"));
        assertThat(success.getContent()).contains("Home Channel");

        HomeChannelRecord home = env.gatewayPolicyRepository.getHomeChannel(PlatformType.MEMORY);
        assertThat(home).isNotNull();
        assertThat(home.getChatId()).isEqualTo("group-1");
        assertThat(home.getChatName()).isEqualTo("Dev Group");

        GatewayReply platforms = env.send("admin-dm", "admin-user", "/platforms");
        assertThat(platforms.getContent()).contains("home=group-1");
        assertThat(platforms.getContent()).contains("admin=admin-user");
    }
}
