package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.llm.SolonAiChatModelFactory;
import com.jimuqu.solon.claw.llm.dialect.RawResponseLoggingChatDialect;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatModel;

/** 验证独立模型协议配置工厂的方言选择与重复构建行为。 */
public class SolonAiChatModelFactoryTest {
    /** 五种受支持线协议都必须使用原始响应日志包装器，且重复构建保持幂等。 */
    @Test
    void shouldBuildWrappedDialectsIdempotentlyForAllSupportedProtocols() {
        SolonAiChatModelFactory factory = new SolonAiChatModelFactory();
        assertWrappedDialect(factory, "openai-responses", "https://example.com/v1/responses");
        assertWrappedDialect(factory, "openai", "https://example.com/v1/chat/completions");
        assertWrappedDialect(factory, "ollama", "http://localhost:11434/api/chat");
        assertWrappedDialect(factory, "gemini", "https://generativelanguage.googleapis.com/v1beta");
        assertWrappedDialect(factory, "anthropic", "https://api.anthropic.com/v1/messages");
        assertWrappedDialect(factory, "openai-responses", "https://example.com/v1/responses");
    }

    /**
     * 断言指定线协议使用预期的日志包装方言。
     *
     * @param factory 模型协议配置工厂。
     * @param dialect 线协议名称。
     * @param apiUrl 协议请求地址。
     */
    private void assertWrappedDialect(
            SolonAiChatModelFactory factory, String dialect, String apiUrl) {
        AppConfig.LlmConfig config = new AppConfig.LlmConfig();
        config.setProvider(dialect);
        config.setDialect(dialect);
        config.setApiUrl(apiUrl);
        config.setModel("test-model");

        ChatModel model = factory.buildModel(config, null);

        assertThat(model.getDialect()).isInstanceOf(RawResponseLoggingChatDialect.class);
        assertThat(((RawResponseLoggingChatDialect) model.getDialect()).getDialectName())
                .isEqualTo(dialect);
    }
}
