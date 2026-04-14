package com.jimuqu.claw.provider;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.provider.model.ResolvedModelConfig;
import org.noear.solon.ai.chat.ChatModel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModelConfigResolver {
    private final ClawProperties properties;
    private final Map<String, ProviderDialect> dialects = new LinkedHashMap<String, ProviderDialect>();

    public ModelConfigResolver(ClawProperties properties, List<ProviderDialect> dialectList) {
        this.properties = properties;
        if (dialectList != null) {
            for (ProviderDialect dialect : dialectList) {
                this.dialects.put(dialect.name(), dialect);
            }
        }
    }

    public ResolvedModelConfig resolve(String modelAlias) {
        String alias = StrUtil.blankToDefault(modelAlias, properties.getRuntime().getDefaultModel());
        ClawProperties.ModelAliasProperties aliasProperties = properties.getModels().get(alias);
        if (aliasProperties == null) {
            throw new IllegalArgumentException("Unknown model alias: " + alias);
        }

        String providerProfileName = aliasProperties.getProviderProfile();
        ClawProperties.ProviderProfileProperties providerProperties = properties.getProviders().get(providerProfileName);
        if (providerProperties == null) {
            throw new IllegalArgumentException("Unknown provider profile: " + providerProfileName);
        }

        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.putAll(providerProperties.getHeaders());
        headers.putAll(aliasProperties.getHeaders());

        String apiKey = StrUtil.blankToDefault(providerProperties.getApiKey(), providerProperties.getToken());

        return ResolvedModelConfig.builder()
                .modelAlias(alias)
                .providerName(providerProfileName)
                .dialect(providerProperties.getDialect())
                .baseUrl(providerProperties.getBaseUrl())
                .apiKey(apiKey)
                .token(providerProperties.getToken())
                .model(aliasProperties.getModel())
                .timeoutMs(providerProperties.getTimeoutMs())
                .temperature(aliasProperties.getTemperature())
                .maxTokens(aliasProperties.getMaxTokens())
                .maxOutputTokens(aliasProperties.getMaxOutputTokens())
                .headers(headers)
                .build();
    }

    public ChatModel buildChatModel(String modelAlias) {
        ResolvedModelConfig resolvedModelConfig = resolve(modelAlias);
        ProviderDialect dialect = dialects.get(resolvedModelConfig.getDialect());
        if (dialect == null) {
            throw new IllegalArgumentException("Unsupported provider dialect: " + resolvedModelConfig.getDialect());
        }

        return dialect.buildChatModel(resolvedModelConfig);
    }
}
