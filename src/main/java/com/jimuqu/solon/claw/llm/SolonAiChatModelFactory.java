package com.jimuqu.solon.claw.llm;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.llm.dialect.LlmDialectRegistrar;
import com.jimuqu.solon.claw.support.constants.LlmConstants;
import com.jimuqu.solon.claw.support.constants.RuntimePathConstants;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.noear.snack4.ONode;
import org.noear.solon.ai.chat.CacheControl;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatModel;

/** 构建 Solon AI 模型协议配置，并隔离提供方请求选项与方言注册细节。 */
public final class SolonAiChatModelFactory {
    /** 单次模型请求的协议层超时时间。 */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    /** 进程级方言注册器。 */
    private final LlmDialectRegistrar dialectRegistrar;

    /** 使用默认方言注册器创建工厂，兼容直接构造网关的调用链。 */
    public SolonAiChatModelFactory() {
        this(new LlmDialectRegistrar());
    }

    /**
     * 使用指定方言注册器创建工厂。
     *
     * @param dialectRegistrar 进程级方言注册器。
     */
    public SolonAiChatModelFactory(LlmDialectRegistrar dialectRegistrar) {
        this.dialectRegistrar =
                dialectRegistrar == null ? new LlmDialectRegistrar() : dialectRegistrar;
    }

    /**
     * 构建带会话原生选项的聊天模型。
     *
     * @param resolved 已解析的大模型配置。
     * @param session 当前会话，可为空。
     * @return 可直接发起协议请求的聊天模型。
     */
    public ChatModel buildModel(AppConfig.LlmConfig resolved, SessionRecord session) {
        return buildConfig(resolved, session).toChatModel();
    }

    /**
     * 构建带会话原生选项的聊天配置。
     *
     * @param resolved 已解析的大模型配置。
     * @param session 当前会话，可为空。
     * @return 聊天协议配置。
     */
    public ChatConfig buildConfig(AppConfig.LlmConfig resolved, SessionRecord session) {
        dialectRegistrar.ensureRegistered();
        String dialect =
                LlmProviderSupport.normalizeDialect(
                        StrUtil.isNotBlank(resolved.getDialect())
                                ? resolved.getDialect()
                                : resolved.getProvider());

        ChatConfig chatConfig = new ChatConfig();
        chatConfig.setApiUrl(resolved.getApiUrl());
        chatConfig.setProvider(dialect);
        chatConfig.setModel(resolved.getModel());
        chatConfig.setTimeout(REQUEST_TIMEOUT);
        if (StrUtil.isNotBlank(resolved.getApiKey())) {
            chatConfig.setApiKey(resolved.getApiKey());
        }

        String reasoningEffort =
                session != null && StrUtil.isNotBlank(session.getReasoningEffortOverride())
                        ? session.getReasoningEffortOverride().trim()
                        : resolved.getReasoningEffort();
        applyProviderRequestOptions(chatConfig, resolved, dialect, reasoningEffort);
        applyFastMode(chatConfig, resolved, dialect, session);
        applyPromptCache(chatConfig, resolved, dialect, session);
        return chatConfig;
    }

    /** 将通用模型配置转换为各协议实际接受的请求字段。 */
    private void applyProviderRequestOptions(
            ChatConfig chatConfig,
            AppConfig.LlmConfig resolved,
            String dialect,
            String reasoningEffort) {
        String effort = StrUtil.nullToEmpty(reasoningEffort).trim().toLowerCase(Locale.ROOT);
        boolean reasoningEnabled = StrUtil.isNotBlank(effort) && !"none".equals(effort);

        if (LlmConstants.PROVIDER_GEMINI.equals(dialect)) {
            Map<String, Object> generationConfig = new LinkedHashMap<String, Object>();
            generationConfig.put("temperature", resolved.getTemperature());
            generationConfig.put(
                    "maxOutputTokens",
                    resolved.getMaxTokens() > 0
                            ? resolved.getMaxTokens()
                            : RuntimePathConstants.DEFAULT_MAX_TOKENS);
            if (StrUtil.isNotBlank(effort)) {
                Map<String, Object> thinkingConfig = new LinkedHashMap<String, Object>();
                thinkingConfig.put("includeThoughts", reasoningEnabled);
                if (reasoningEnabled
                        && !resolved.getModel()
                                .toLowerCase(Locale.ROOT)
                                .startsWith("gemini-2.5-")) {
                    if ("minimal".equals(effort) || "low".equals(effort)) {
                        thinkingConfig.put("thinkingLevel", "LOW");
                    } else if ("high".equals(effort) || "xhigh".equals(effort)) {
                        thinkingConfig.put("thinkingLevel", "HIGH");
                    }
                }
                generationConfig.put("thinkingConfig", thinkingConfig);
            }
            // Solon AI 4.0.3 的 Gemini Map 转换会丢失嵌套配置，结构化节点由官方方言原样输出。
            chatConfig
                    .getModelOptions()
                    .optionSet("generationConfig", ONode.ofBean(generationConfig));
            return;
        }

        if (LlmConstants.PROVIDER_OLLAMA.equals(dialect)) {
            Map<String, Object> options = new LinkedHashMap<String, Object>();
            options.put("temperature", resolved.getTemperature());
            if (resolved.getMaxTokens() > 0) {
                options.put("num_predict", resolved.getMaxTokens());
            }
            chatConfig.getModelOptions().optionSet("options", options);
            if (StrUtil.isNotBlank(effort)) {
                chatConfig.getModelOptions().optionSet("think", reasoningEnabled);
            }
            return;
        }

        if (resolved.getMaxTokens() > 0) {
            chatConfig.getModelOptions().max_tokens(resolved.getMaxTokens());
        }
        if (!reasoningEnabled
                || (!LlmConstants.PROVIDER_ANTHROPIC.equals(dialect)
                        && !supportsOpenAiReasoning(resolved.getModel()))) {
            chatConfig.getModelOptions().temperature(resolved.getTemperature());
        }

        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect) && reasoningEnabled) {
            Map<String, Object> reasoning = new LinkedHashMap<String, Object>();
            reasoning.put("effort", effort);
            reasoning.put("summary", "auto");
            chatConfig.getModelOptions().optionSet("reasoning", reasoning);
        } else if (LlmConstants.PROVIDER_OPENAI.equals(dialect)
                && reasoningEnabled
                && supportsOpenAiReasoning(resolved.getModel())) {
            chatConfig.getModelOptions().optionSet("reasoning_effort", effort);
        } else if (LlmConstants.PROVIDER_ANTHROPIC.equals(dialect) && reasoningEnabled) {
            applyAnthropicReasoning(chatConfig, resolved, effort);
        }
    }

    /** 将推理强度转换为 Anthropic 自适应或预算式 thinking 参数。 */
    private void applyAnthropicReasoning(
            ChatConfig chatConfig, AppConfig.LlmConfig resolved, String effort) {
        if (usesAdaptiveAnthropicThinking(resolved.getModel())) {
            Map<String, Object> thinking = new LinkedHashMap<String, Object>();
            thinking.put("type", "adaptive");
            chatConfig.getModelOptions().optionSet("thinking", thinking);
            Map<String, Object> outputConfig = new LinkedHashMap<String, Object>();
            outputConfig.put("effort", normalizeAnthropicEffort(resolved.getModel(), effort));
            chatConfig.getModelOptions().optionSet("output_config", outputConfig);
            return;
        }

        int maxTokens = resolved.getMaxTokens();
        if (maxTokens <= 1024) {
            return;
        }
        int requestedBudget;
        if ("minimal".equals(effort)) {
            requestedBudget = 1024;
        } else if ("low".equals(effort)) {
            requestedBudget = 4000;
        } else if ("high".equals(effort)) {
            requestedBudget = 16000;
        } else if ("xhigh".equals(effort)) {
            requestedBudget = 32000;
        } else {
            requestedBudget = 8000;
        }
        Map<String, Object> thinking = new LinkedHashMap<String, Object>();
        thinking.put("enabled", true);
        thinking.put("budget_tokens", Math.min(requestedBudget, maxTokens - 1024));
        chatConfig.getModelOptions().optionSet("thinking", thinking);
    }

    /** 判断 Claude 模型是否使用 4.6 之后的自适应 thinking 合约。 */
    private boolean usesAdaptiveAnthropicThinking(String model) {
        String normalized = StrUtil.nullToEmpty(model).toLowerCase(Locale.ROOT).replace('.', '-');
        if (!normalized.contains("claude")) {
            return false;
        }
        return !(normalized.contains("claude-3")
                || normalized.contains("opus-4-0")
                || normalized.contains("opus-4-1")
                || normalized.contains("opus-4-5")
                || normalized.contains("sonnet-4-0")
                || normalized.contains("sonnet-4-5")
                || normalized.contains("haiku-4-5"));
    }

    /** 规范化 Anthropic 自适应 thinking 的 effort 值。 */
    private String normalizeAnthropicEffort(String model, String effort) {
        if ("minimal".equals(effort)) {
            return "low";
        }
        String normalizedModel = StrUtil.nullToEmpty(model).toLowerCase(Locale.ROOT);
        if ("xhigh".equals(effort)
                && (normalizedModel.contains("4-6") || normalizedModel.contains("4.6"))) {
            return "max";
        }
        return effort;
    }

    /** 判断 OpenAI 模型是否接受 reasoning effort。 */
    private boolean supportsOpenAiReasoning(String model) {
        String normalized = StrUtil.nullToEmpty(model).toLowerCase(Locale.ROOT);
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        return normalized.startsWith("gpt-5")
                || normalized.startsWith("o1")
                || normalized.startsWith("o3")
                || normalized.startsWith("o4");
    }

    /** 将会话快速模式转换为提供方原生请求参数。 */
    private void applyFastMode(
            ChatConfig chatConfig,
            AppConfig.LlmConfig resolved,
            String dialect,
            SessionRecord session) {
        if (session == null
                || !"priority"
                        .equalsIgnoreCase(
                                StrUtil.nullToEmpty(session.getServiceTierOverride()).trim())) {
            return;
        }
        if ((LlmConstants.PROVIDER_OPENAI.equals(dialect)
                        || LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect))
                && LlmProviderSupport.isDirectOpenAiBaseUrl(resolved.getApiUrl())
                && supportsOpenAiReasoning(resolved.getModel())
                && !StrUtil.nullToEmpty(resolved.getModel())
                        .toLowerCase(Locale.ROOT)
                        .contains("codex")) {
            chatConfig.getModelOptions().optionSet("service_tier", "priority");
            return;
        }
        if (LlmConstants.PROVIDER_ANTHROPIC.equals(dialect)
                && LlmProviderSupport.baseUrlHostMatches(resolved.getApiUrl(), "api.anthropic.com")
                && isAnthropicFastModel(resolved.getModel())) {
            chatConfig.getModelOptions().optionSet("speed", "fast");
            chatConfig.setHeader("anthropic-beta", "fast-mode-2026-02-01");
        }
    }

    /** 判断模型是否支持 Anthropic 快速模式。 */
    private boolean isAnthropicFastModel(String model) {
        String normalized = StrUtil.nullToEmpty(model).toLowerCase(Locale.ROOT);
        return normalized.contains("opus-4-6") || normalized.contains("opus-4.6");
    }

    /** 将提示词缓存配置接入 Solon AI 官方缓存控制与协议补充策略。 */
    private void applyPromptCache(
            ChatConfig chatConfig,
            AppConfig.LlmConfig resolved,
            String dialect,
            SessionRecord session) {
        PromptCachePolicy policy = new PromptCachePolicy(resolved.getPromptCache());
        if (!policy.isEnabled()) {
            return;
        }
        if (LlmConstants.PROVIDER_ANTHROPIC.equals(dialect)) {
            chatConfig.setCacheControl(CacheControl.ofEphemeral());
            chatConfig.getModelOptions().toolContextPut(PromptCachePolicy.TOOL_CONTEXT_KEY, policy);
            return;
        }

        String seed =
                StrUtil.nullToEmpty(resolved.getProvider())
                        + '|'
                        + StrUtil.nullToEmpty(resolved.getModel())
                        + '|'
                        + (session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()));
        String cacheKey = "solonclaw-" + SecureUtil.sha256(seed).substring(0, 32);
        if (LlmConstants.PROVIDER_OPENAI.equals(dialect)) {
            chatConfig.setCacheControl(CacheControl.ofPromptKey(cacheKey));
        } else if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect)) {
            chatConfig.getModelOptions().optionSet("prompt_cache_key", cacheKey);
        }
    }
}
