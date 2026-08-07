package com.jimuqu.solon.claw.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatOptions;
import org.noear.solon.ai.chat.ChatRequest;
import org.noear.solon.ai.chat.ChatResponseDefault;
import org.noear.solon.ai.chat.session.InMemoryChatSession;
import org.noear.solon.ai.llm.dialect.openai.OpenaiChatDialect;

/** 验证 Solon AI 响应边界兼容处理。 */
public class SolonAiLlmGatewayResponseTest {
    /** 仅包含 usage 的流事件没有 choices，读取结束原因前必须安全返回空选择项。 */
    @Test
    void shouldAcceptUsageOnlyStreamResponseWithoutChoice() {
        ChatRequest request =
                new ChatRequest(
                        new ChatConfig(),
                        OpenaiChatDialect.getInstance(),
                        new ChatOptions(),
                        new InMemoryChatSession("usage-only-test"),
                        null,
                        null,
                        true);
        ChatResponseDefault response = new ChatResponseDefault(request, true);

        assertThat(SolonAiLlmGateway.lastChoiceOrNull(response)).isNull();
    }
}
