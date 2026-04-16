package com.jimuqu.agent.llm;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.support.constants.LlmConstants;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.session.InMemoryChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SolonAiLlmGateway 实现。
 */
@RequiredArgsConstructor
public class SolonAiLlmGateway implements LlmGateway {
    /**
     * LLM 网关日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(SolonAiLlmGateway.class);

    private final AppConfig appConfig;

    public LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception {
        AppConfig.LlmConfig resolved = resolve(session);
        validate(resolved);
        InMemoryChatSession chatSession = new InMemoryChatSession(session.getSessionId());
        if (session.getNdjson() != null && session.getNdjson().trim().length() > 0) {
            chatSession.loadNdjson(session.getNdjson());
        }

        ChatModel.Builder builder = ChatModel.of(resolved.getApiUrl())
                .provider(resolved.getProvider())
                .model(resolved.getModel())
                .timeout(Duration.ofMinutes(5))
                .systemPrompt(systemPrompt);

        if (resolved.getApiKey() != null && resolved.getApiKey().trim().length() > 0) {
            builder.apiKey(resolved.getApiKey());
        }

        for (Object toolObject : toolObjects) {
            builder.defaultToolAdd(toolObject);
        }

        builder.modelOptions(options -> {
            options.temperature(resolved.getTemperature());
            options.max_tokens(resolved.getMaxTokens());
            if (resolved.getReasoningEffort() != null && resolved.getReasoningEffort().trim().length() > 0) {
                Map<String, Object> reasoning = new HashMap<String, Object>();
                reasoning.put("effort", resolved.getReasoningEffort());
                options.optionSet("reasoning", reasoning);
            }
        });

        ChatModel chatModel = builder.build();
        ChatResponse response = invoke(chatModel, chatSession, userMessage, resolved.isStream());
        if (response == null) {
            throw new IllegalStateException("模型未返回可解析响应。");
        }

        AssistantMessage assistantMessage = response.getAggregationMessage() != null
                ? response.getAggregationMessage()
                : response.getMessage();

        LlmResult result = new LlmResult();
        result.setAssistantMessage(assistantMessage);
        result.setNdjson(chatSession.toNdjson());
        result.setStreamed(resolved.isStream());
        result.setRawResponse(stringify(response.getResponseData()));
        return result;
    }

    private AppConfig.LlmConfig resolve(SessionRecord session) {
        AppConfig.LlmConfig current = new AppConfig.LlmConfig();
        current.setProvider(StrUtil.nullToEmpty(appConfig.getLlm().getProvider()).trim().toLowerCase());
        current.setApiUrl(StrUtil.nullToEmpty(appConfig.getLlm().getApiUrl()).trim());
        current.setApiKey(appConfig.getLlm().getApiKey());
        current.setModel(StrUtil.nullToEmpty(appConfig.getLlm().getModel()).trim());
        current.setStream(appConfig.getLlm().isStream());
        current.setReasoningEffort(StrUtil.nullToEmpty(appConfig.getLlm().getReasoningEffort()).trim());
        current.setTemperature(appConfig.getLlm().getTemperature());
        current.setMaxTokens(appConfig.getLlm().getMaxTokens());
        current.setContextWindowTokens(appConfig.getLlm().getContextWindowTokens());

        if (session == null || session.getModelOverride() == null || session.getModelOverride().trim().length() == 0) {
            return current;
        }

        String override = session.getModelOverride().trim();
        if (override.contains(":")) {
            String[] parts = override.split(":", 2);
            current.setProvider(StrUtil.nullToEmpty(parts[0]).trim().toLowerCase());
            current.setModel(StrUtil.nullToEmpty(parts[1]).trim());
        } else {
            current.setModel(override.trim());
        }

        return current;
    }

    /**
     * 统一执行流式或非流式调用；流式异常时退回非流式，避免主链直接中断。
     */
    private ChatResponse invoke(ChatModel chatModel,
                                InMemoryChatSession chatSession,
                                String userMessage,
                                boolean preferStream) throws Exception {
        if (preferStream) {
            try {
                ChatResponse response = Flux.from(chatModel.prompt(userMessage)
                                .session(chatSession)
                                .options(options -> options.toolContextPut("user_message", userMessage))
                                .stream())
                        .reduce((ignored, item) -> item)
                        .block();
                if (response != null) {
                    return response;
                }
                log.warn("LLM stream returned empty response, fallback to non-stream call");
            } catch (Exception e) {
                if (!isRecoverableStreamFailure(e)) {
                    throw e;
                }
                log.warn("LLM stream call failed, fallback to non-stream call: {}", e.getMessage());
            }
        }

        return chatModel.prompt(userMessage)
                .session(chatSession)
                .options(options -> options.toolContextPut("user_message", userMessage))
                .call();
    }

    /**
     * 校验 provider 与 URL 配置，避免隐式降级到错误协议。
     */
    private void validate(AppConfig.LlmConfig resolved) {
        if (StrUtil.isBlank(resolved.getProvider())) {
            throw new IllegalStateException("LLM provider 不能为空。");
        }
        if (!LlmConstants.SUPPORTED_PROVIDERS.contains(resolved.getProvider())) {
            throw new IllegalStateException("不支持的 provider：" + resolved.getProvider());
        }
        if (StrUtil.isBlank(resolved.getApiUrl())) {
            throw new IllegalStateException("LLM apiUrl 不能为空。");
        }
        if (StrUtil.isBlank(resolved.getModel())) {
            throw new IllegalStateException("LLM model 不能为空。");
        }
        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(resolved.getProvider())
                && !StrUtil.containsIgnoreCase(resolved.getApiUrl(), "/responses")) {
            throw new IllegalStateException("openai-responses 的 apiUrl 必须直接指向 /responses 接口。");
        }
    }

    /**
     * 判断是否属于可退回非流式的协议异常。
     */
    private boolean isRecoverableStreamFailure(Exception e) {
        String message = stringify(e.getMessage()).toLowerCase();
        return message.contains("json")
                || message.contains("decode")
                || message.contains("content-type")
                || message.contains("unexpected end")
                || message.contains("connection reset")
                || message.contains("timeout")
                || message.contains("eof");
    }

    /**
     * 将底层原始响应安全转为字符串。
     */
    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
