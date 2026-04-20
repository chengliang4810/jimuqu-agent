package com.jimuqu.agent;

import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelCommandTest {
    @Test
    void shouldShowAndSetSessionAndGlobalModel() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");

        GatewayReply showReply = env.send("admin-chat", "admin-user", "/model");
        assertThat(showReply.getContent()).contains("current.provider=").contains("global.model=");

        GatewayReply sessionReply = env.send("admin-chat", "admin-user", "/model openai:gpt-5.2");
        assertThat(sessionReply.getContent()).contains("openai:gpt-5.2");
        assertThat(env.sessionRepository.getBoundSession("MEMORY:admin-chat:admin-user").getModelOverride()).isEqualTo("openai:gpt-5.2");

        GatewayReply globalReply = env.send("admin-chat", "admin-user", "/model --global anthropic:claude-sonnet-4");
        assertThat(globalReply.getContent()).contains("anthropic:claude-sonnet-4");
        assertThat(env.appConfig.getLlm().getProvider()).isEqualTo("anthropic");
        assertThat(env.appConfig.getLlm().getModel()).isEqualTo("claude-sonnet-4");
    }
}
