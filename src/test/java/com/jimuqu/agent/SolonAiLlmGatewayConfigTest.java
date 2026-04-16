package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.llm.SolonAiLlmGateway;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 校验 LLM provider 配置的前置失败逻辑。
 */
public class SolonAiLlmGatewayConfigTest {
    @Test
    void shouldFailFastForUnsupportedProvider() {
        AppConfig config = new AppConfig();
        config.getLlm().setProvider("unknown-provider");
        config.getLlm().setApiUrl("https://example.com/v1/chat/completions");
        config.getLlm().setModel("test-model");

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-1");

        assertThatThrownBy(() -> gateway.chat(session, "system", "hello", Collections.emptyList()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持的 provider");
    }

    @Test
    void shouldRequireResponsesEndpointForOpenaiResponsesProvider() {
        AppConfig config = new AppConfig();
        config.getLlm().setProvider("openai-responses");
        config.getLlm().setApiUrl("https://example.com/v1/chat/completions");
        config.getLlm().setModel("gpt-5.4");

        SolonAiLlmGateway gateway = new SolonAiLlmGateway(config);
        SessionRecord session = new SessionRecord();
        session.setSessionId("s-2");

        assertThatThrownBy(() -> gateway.chat(session, "system", "hello", Collections.emptyList()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/responses");
    }
}
