package com.jimuqu.claw.provider;

import com.jimuqu.claw.provider.model.ResolvedModelConfig;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatModel;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class StandardProviderDialect implements ProviderDialect {
    private final String name;

    public StandardProviderDialect(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ChatModel buildChatModel(ResolvedModelConfig modelConfig) {
        ChatConfig config = new ChatConfig();
        config.setProvider(modelConfig.getDialect());
        config.setApiUrl(modelConfig.getBaseUrl());
        config.setApiKey(modelConfig.getApiKey());
        config.setModel(modelConfig.getModel());
        config.setHeaders(modelConfig.getHeaders());
        config.setTimeout(Duration.ofMillis(modelConfig.getTimeoutMs()));

        for (Map.Entry<String, Object> entry : buildModelOptions(modelConfig).entrySet()) {
            config.getModelOptions().optionSet(entry.getKey(), entry.getValue());
        }

        return ChatModel.of(config).build();
    }

    Map<String, Object> buildModelOptions(ResolvedModelConfig modelConfig) {
        Map<String, Object> options = new LinkedHashMap<String, Object>();

        if (modelConfig.getTemperature() != null) {
            options.put("temperature", modelConfig.getTemperature());
        }

        String dialect = modelConfig.getDialect();
        if ("openai-responses".equals(dialect)) {
            Long maxOutputTokens = firstNonNull(modelConfig.getMaxOutputTokens(), modelConfig.getMaxTokens());
            if (maxOutputTokens != null) {
                // Solon-AI Responses builder maps max_tokens -> max_output_tokens.
                options.put("max_tokens", maxOutputTokens);
            }
            return options;
        }

        if ("gemini".equals(dialect)) {
            Long maxOutputTokens = firstNonNull(modelConfig.getMaxOutputTokens(), modelConfig.getMaxTokens());
            if (maxOutputTokens != null) {
                options.put("maxOutputTokens", maxOutputTokens);
            }
            return options;
        }

        Long maxTokens = firstNonNull(modelConfig.getMaxTokens(), modelConfig.getMaxOutputTokens());
        if (maxTokens != null) {
            options.put("max_tokens", maxTokens);
        }

        return options;
    }

    private Long firstNonNull(Long primary, Long fallback) {
        return primary != null ? primary : fallback;
    }
}
