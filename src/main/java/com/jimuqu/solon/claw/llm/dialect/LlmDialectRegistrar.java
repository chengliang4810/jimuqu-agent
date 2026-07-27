package com.jimuqu.solon.claw.llm.dialect;

import com.jimuqu.solon.claw.support.constants.LlmConstants;
import java.util.concurrent.atomic.AtomicBoolean;
import org.noear.solon.ai.chat.dialect.ChatDialectManager;
import org.noear.solon.ai.llm.dialect.anthropic.AnthropicChatDialect;
import org.noear.solon.ai.llm.dialect.gemini.GeminiChatDialect;
import org.noear.solon.ai.llm.dialect.ollama.OllamaChatDialect;
import org.noear.solon.ai.llm.dialect.openai.OpenaiChatDialect;
import org.noear.solon.ai.llm.dialect.openai.OpenaiResponsesDialect;

/** 统一注册带原始响应日志能力的模型线协议方言。 */
public final class LlmDialectRegistrar {
    /** 保证进程级方言注册表只执行一次自定义注册。 */
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    /** 幂等注册当前支持的五种线协议方言。 */
    public void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ChatDialectManager.register(
                new RawResponseLoggingChatDialect(
                        OpenaiResponsesDialect.getInstance(),
                        LlmConstants.PROVIDER_OPENAI_RESPONSES,
                        true),
                -100);
        ChatDialectManager.register(
                new RawResponseLoggingChatDialect(
                        OpenaiChatDialect.getInstance(), LlmConstants.PROVIDER_OPENAI, false),
                -99);
        ChatDialectManager.register(
                new RawResponseLoggingChatDialect(
                        OllamaChatDialect.getInstance(), LlmConstants.PROVIDER_OLLAMA, false),
                -98);
        ChatDialectManager.register(
                new RawResponseLoggingChatDialect(
                        GeminiChatDialect.getInstance(), LlmConstants.PROVIDER_GEMINI, false),
                -97);
        ChatDialectManager.register(
                new RawResponseLoggingChatDialect(
                        AnthropicChatDialect.getInstance(), LlmConstants.PROVIDER_ANTHROPIC, false),
                -96);
    }
}
