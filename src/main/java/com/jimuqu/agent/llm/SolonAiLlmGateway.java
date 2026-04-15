package com.jimuqu.agent.llm;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.ai.chat.session.InMemoryChatSession;
import org.noear.solon.ai.chat.message.AssistantMessage;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SolonAiLlmGateway 实现。
 */
public class SolonAiLlmGateway implements LlmGateway {
    private final AppConfig appConfig;

    public SolonAiLlmGateway(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception {
        AppConfig.LlmConfig resolved = resolve(session);
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
        ChatResponse response;
        if (resolved.isStream()) {
            response = Flux.from(chatModel.prompt(userMessage)
                            .session(chatSession)
                            .options(options -> options.toolContextPut("user_message", userMessage))
                            .stream())
                    .reduce((ignored, item) -> item)
                    .block();
        } else {
            response = chatModel.prompt(userMessage)
                    .session(chatSession)
                    .options(options -> options.toolContextPut("user_message", userMessage))
                    .call();
        }

        AssistantMessage assistantMessage = response.getAggregationMessage() != null
                ? response.getAggregationMessage()
                : response.getMessage();

        LlmResult result = new LlmResult();
        result.setAssistantMessage(assistantMessage);
        result.setNdjson(chatSession.toNdjson());
        result.setStreamed(resolved.isStream());
        result.setRawResponse(response.getResponseData());
        return result;
    }

    private AppConfig.LlmConfig resolve(SessionRecord session) {
        AppConfig.LlmConfig current = new AppConfig.LlmConfig();
        current.setProvider(appConfig.getLlm().getProvider());
        current.setApiUrl(appConfig.getLlm().getApiUrl());
        current.setApiKey(appConfig.getLlm().getApiKey());
        current.setModel(appConfig.getLlm().getModel());
        current.setStream(appConfig.getLlm().isStream());
        current.setReasoningEffort(appConfig.getLlm().getReasoningEffort());
        current.setTemperature(appConfig.getLlm().getTemperature());
        current.setMaxTokens(appConfig.getLlm().getMaxTokens());

        if (session == null || session.getModelOverride() == null || session.getModelOverride().trim().length() == 0) {
            return current;
        }

        String override = session.getModelOverride().trim();
        if (override.contains(":")) {
            String[] parts = override.split(":", 2);
            current.setProvider(parts[0]);
            current.setModel(parts[1]);
        } else {
            current.setModel(override);
        }

        return current;
    }
}
