package com.jimuqu.agent;

import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.PlatformAdminRecord;
import com.jimuqu.agent.support.TestEnvironment;
import com.jimuqu.agent.support.constants.GatewayBehaviorConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GatewayAuthorizationServiceTest {
    @Test
    void shouldHonorQqbotChannelAllowAllUsers() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getChannels().getQqbot().setAllowAllUsers(true);

        GatewayMessage message = new GatewayMessage(PlatformType.QQBOT, "chat", "qq-user", "hello");

        assertThat(env.gatewayAuthorizationService.isAuthorized(message)).isTrue();
    }

    @Test
    void shouldHonorYuanbaoChannelAllowlistAndUnauthorizedBehavior() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getChannels().getYuanbao().getAllowedUsers().add("allowed-user");
        env.appConfig.getChannels().getYuanbao().setUnauthorizedDmBehavior(GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE);
        createAdmin(env, PlatformType.YUANBAO);

        GatewayMessage allowed = new GatewayMessage(PlatformType.YUANBAO, "chat", "allowed-user", "hello");
        GatewayMessage stranger = new GatewayMessage(PlatformType.YUANBAO, "chat", "stranger", "hello");

        assertThat(env.gatewayAuthorizationService.isAuthorized(allowed)).isTrue();
        GatewayReply preAuth = env.gatewayAuthorizationService.preAuthorize(stranger);
        assertThat(preAuth).isNull();
    }

    private void createAdmin(TestEnvironment env, PlatformType platform) throws Exception {
        PlatformAdminRecord admin = new PlatformAdminRecord();
        admin.setPlatform(platform);
        admin.setUserId("admin-user");
        admin.setUserName("admin");
        admin.setChatId("admin-chat");
        admin.setCreatedAt(System.currentTimeMillis());
        env.gatewayPolicyRepository.createPlatformAdminIfAbsent(admin);
    }
}
