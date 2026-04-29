package com.jimuqu.agent.llm;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.config.RuntimeConfigResolver;
import com.jimuqu.agent.core.model.AgentRunContext;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.ConversationEventSink;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.gateway.feedback.ConversationFeedbackSink;
import com.jimuqu.agent.gateway.feedback.ToolPreviewSupport;
import com.jimuqu.agent.llm.dialect.LoggingOpenaiChatDialect;
import com.jimuqu.agent.llm.dialect.LoggingOpenaiResponsesDialect;
import com.jimuqu.agent.storage.session.SqliteAgentSession;
import com.jimuqu.agent.support.LlmProviderService;
import com.jimuqu.agent.support.constants.LlmConstants;
import com.jimuqu.agent.tool.runtime.DangerousCommandApprovalService;
import org.noear.solon.ai.agent.AgentSession;
import org.noear.solon.ai.agent.AgentSessionProvider;
import org.noear.solon.ai.agent.trace.Metrics;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActResponse;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.agent.react.intercept.SummarizationInterceptor;
import org.noear.solon.ai.agent.react.intercept.ToolRetryInterceptor;
import org.noear.solon.ai.agent.react.intercept.ToolSanitizerInterceptor;
import org.noear.solon.ai.agent.react.intercept.summarize.CompositeSummarizationStrategy;
import org.noear.solon.ai.agent.react.intercept.summarize.HierarchicalSummarizationStrategy;
import org.noear.solon.ai.agent.react.intercept.summarize.KeyInfoExtractionStrategy;
import org.noear.solon.ai.chat.ChatConfig;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatRequestDesc;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.ai.chat.dialect.ChatDialectManager;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.prompt.Prompt;
import org.noear.solon.ai.harness.HarnessEngine;
import org.noear.solon.ai.harness.HarnessProperties;
import org.noear.solon.ai.harness.agent.AgentDefinition;
import org.noear.solon.ai.skills.pdf.PdfSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * SolonAiLlmGateway 实现。
 */
public class SolonAiLlmGateway implements LlmGateway {
    /**
     * LLM 网关日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(SolonAiLlmGateway.class);
    private static final AtomicBoolean CUSTOM_DIALECTS_REGISTERED = new AtomicBoolean(false);

    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final DangerousCommandApprovalService dangerousCommandApprovalService;
    private final LlmProviderService llmProviderService;
    private volatile PdfSkill pdfSkill;

    public SolonAiLlmGateway(AppConfig appConfig) {
        this(appConfig, null, null, null);
    }

    public SolonAiLlmGateway(AppConfig appConfig, SessionRepository sessionRepository) {
        this(appConfig, sessionRepository, null, null);
    }

    public SolonAiLlmGateway(AppConfig appConfig,
                             SessionRepository sessionRepository,
                             DangerousCommandApprovalService dangerousCommandApprovalService) {
        this(appConfig, sessionRepository, dangerousCommandApprovalService, null);
    }

    public SolonAiLlmGateway(AppConfig appConfig,
                             SessionRepository sessionRepository,
                             DangerousCommandApprovalService dangerousCommandApprovalService,
                             LlmProviderService llmProviderService) {
        this.appConfig = appConfig;
        this.sessionRepository = sessionRepository;
        this.dangerousCommandApprovalService = dangerousCommandApprovalService;
        this.llmProviderService = llmProviderService == null ? new LlmProviderService(appConfig) : llmProviderService;
    }

    @Override
    public LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception {
        return chat(session, systemPrompt, userMessage, toolObjects, ConversationFeedbackSink.noop());
    }

    @Override
    public LlmResult chat(SessionRecord session,
                          String systemPrompt,
                          String userMessage,
                          List<Object> toolObjects,
                          ConversationFeedbackSink feedbackSink) throws Exception {
        return chat(session, systemPrompt, userMessage, toolObjects, feedbackSink, ConversationEventSink.noop());
    }

    @Override
    public LlmResult chat(SessionRecord session,
                          String systemPrompt,
                          String userMessage,
                          List<Object> toolObjects,
                          ConversationFeedbackSink feedbackSink,
                          ConversationEventSink eventSink) throws Exception {
        return executeWithFailover(session, systemPrompt, userMessage, toolObjects, feedbackSink, eventSink, false);
    }

    @Override
    public LlmResult resume(SessionRecord session, String systemPrompt, List<Object> toolObjects) throws Exception {
        return resume(session, systemPrompt, toolObjects, ConversationFeedbackSink.noop());
    }

    @Override
    public LlmResult resume(SessionRecord session,
                            String systemPrompt,
                            List<Object> toolObjects,
                            ConversationFeedbackSink feedbackSink) throws Exception {
        return resume(session, systemPrompt, toolObjects, feedbackSink, ConversationEventSink.noop());
    }

    @Override
    public LlmResult resume(SessionRecord session,
                            String systemPrompt,
                            List<Object> toolObjects,
                            ConversationFeedbackSink feedbackSink,
                            ConversationEventSink eventSink) throws Exception {
        return executeWithFailover(session, systemPrompt, null, toolObjects, feedbackSink, eventSink, true);
    }

    @Override
    public LlmResult executeOnce(SessionRecord session,
                                 String systemPrompt,
                                 String userMessage,
                                 List<Object> toolObjects,
                                 ConversationFeedbackSink feedbackSink,
                                 ConversationEventSink eventSink,
                                 boolean resume,
                                 AppConfig.LlmConfig resolved,
                                 AgentRunContext runContext) throws Exception {
        return executeSingle(session, systemPrompt, userMessage, toolObjects, feedbackSink, eventSink, resume, resolved, runContext);
    }

    private LlmResult executeWithFailover(SessionRecord session,
                                          String systemPrompt,
                                          String userMessage,
                                          List<Object> toolObjects,
                                          ConversationFeedbackSink feedbackSink,
                                          ConversationEventSink eventSink,
                                          boolean resume) throws Exception {
        List<AppConfig.LlmConfig> candidates = buildCandidateConfigs(session);
        Throwable lastError = null;
        boolean primary = true;

        for (AppConfig.LlmConfig resolved : candidates) {
            int maxAttempts = 2;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    LlmResult result = executeSingle(session, systemPrompt, userMessage, toolObjects, feedbackSink, eventSink, resume, resolved);
                    if (hasVisibleContent(result.getAssistantMessage(), result.getRawResponse())) {
                        return result;
                    }
                    if (attempt < maxAttempts) {
                        log.warn("LLM empty response, retrying same provider: provider={}, dialect={}, model={}, attempt={}",
                                resolved.getProvider(), resolved.getDialect(), resolved.getModel(), attempt);
                        continue;
                    }
                    lastError = new IllegalStateException("LLM returned empty assistant content");
                } catch (Exception e) {
                    lastError = e;
                    FailureMode failureMode = classifyFailure(e);
                    if (failureMode.retryable && attempt < maxAttempts) {
                        log.warn("LLM request failed, retrying same provider: provider={}, dialect={}, model={}, attempt={}, message={}",
                                resolved.getProvider(), resolved.getDialect(), resolved.getModel(), attempt, e.getMessage());
                        continue;
                    }
                }

                if (!primary) {
                    log.warn("Fallback provider failed, trying next candidate: provider={}, dialect={}, model={}, message={}",
                            resolved.getProvider(), resolved.getDialect(), resolved.getModel(),
                            lastError == null ? "" : lastError.getMessage());
                } else {
                    log.warn("Primary provider failed, switching to fallback candidate: provider={}, dialect={}, model={}, message={}",
                            resolved.getProvider(), resolved.getDialect(), resolved.getModel(),
                            lastError == null ? "" : lastError.getMessage());
                }
                break;
            }
            primary = false;
        }

        if (lastError instanceof Exception) {
            throw (Exception) lastError;
        }
        throw new IllegalStateException(lastError == null ? "LLM execution failed" : lastError.getMessage(), lastError);
    }

    protected LlmResult executeSingle(SessionRecord session,
                                      String systemPrompt,
                                      String userMessage,
                                      List<Object> toolObjects,
                                      ConversationFeedbackSink feedbackSink,
                                      ConversationEventSink eventSink,
                                      boolean resume,
                                      AppConfig.LlmConfig resolved) throws Exception {
        return executeSingle(session, systemPrompt, userMessage, toolObjects, feedbackSink, eventSink, resume, resolved, null);
    }

    protected LlmResult executeSingle(SessionRecord session,
                                      String systemPrompt,
                                      String userMessage,
                                      List<Object> toolObjects,
                                      ConversationFeedbackSink feedbackSink,
                                      ConversationEventSink eventSink,
                                      boolean resume,
                                      AppConfig.LlmConfig resolved,
                                      AgentRunContext runContext) throws Exception {
        validate(resolved);
        log.info("LLM {}: provider={}, dialect={}, model={}, sessionId={}, stream={}, sessionOverride={}",
                resume ? "resume" : "request",
                resolved.getProvider(),
                resolved.getDialect(),
                resolved.getModel(),
                session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()),
                resolved.isStream(),
                session != null && StrUtil.isNotBlank(session.getModelOverride()));
        SqliteAgentSession agentSession = new SqliteAgentSession(session, sessionRepository);
        ChatConfig chatConfig = buildChatConfig(resolved);
        ReActAgent agent = buildHarnessReActAgent(chatConfig, resolved, systemPrompt, toolObjects, agentSession, feedbackSink, runContext);
        if (eventSink != null && eventSink != ConversationEventSink.noop()) {
            return callAgentStream(agent, agentSession, session, userMessage, resume, resolved, eventSink);
        }
        ReActResponse response = callAgent(agent, agentSession, userMessage, resume);

        AssistantMessage assistantMessage = response.getMessage();
        LlmResult result = new LlmResult();
        result.setAssistantMessage(assistantMessage);
        result.setNdjson(ChatMessage.toNdjson(agentSession.getMessages()));
        result.setStreamed(resolved.isStream());
        result.setRawResponse(response.getContent());
        result.setProvider(resolved.getProvider());
        result.setModel(StrUtil.blankToDefault(resolved.getModel(), ""));
        applyMetrics(result, response.getMetrics());
        logUsage(session, resolved, result);
        return result;
    }

    private ReActResponse callAgent(ReActAgent agent,
                                    SqliteAgentSession agentSession,
                                    String userMessage,
                                    boolean resume) throws Exception {
        try {
            if (resume) {
                return agent.prompt()
                        .session(agentSession)
                        .call();
            }
            return agent.prompt(Prompt.of(userMessage))
                    .session(agentSession)
                    .options(options -> options.toolContextPut("user_message", userMessage))
                    .call();
        } catch (Exception e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("ReActAgent call failed", e);
        }
    }

    private LlmResult callAgentStream(ReActAgent agent,
                                      SqliteAgentSession agentSession,
                                      SessionRecord session,
                                      String userMessage,
                                      boolean resume,
                                      AppConfig.LlmConfig resolved,
                                      ConversationEventSink eventSink) throws Exception {
        final StringBuilder emittedText = new StringBuilder();
        final ReActResponse[] finalResponse = new ReActResponse[1];

        try {
            if (resume) {
                agent.prompt()
                        .session(agentSession)
                        .stream()
                        .doOnNext(chunk -> handleStreamChunk(chunk, emittedText, eventSink, finalResponse))
                        .blockLast();
            } else {
                agent.prompt(Prompt.of(userMessage))
                        .session(agentSession)
                        .options(options -> options.toolContextPut("user_message", userMessage))
                        .stream()
                        .doOnNext(chunk -> handleStreamChunk(chunk, emittedText, eventSink, finalResponse))
                        .blockLast();
            }
        } catch (Throwable e) {
            if (e instanceof Exception) {
                throw (Exception) e;
            }
            throw new IllegalStateException("ReActAgent stream failed", e);
        }

        AssistantMessage assistantMessage = finalResponse[0] == null ? ChatMessage.ofAssistant(emittedText.toString()) : finalResponse[0].getMessage();
        String finalText = extractText(assistantMessage);
        String emitted = emittedText.toString();
        if (StrUtil.isNotBlank(finalText) && finalText.startsWith(emitted)) {
            String tail = finalText.substring(emitted.length());
            if (StrUtil.isNotBlank(tail)) {
                eventSink.onAssistantDelta(tail);
            }
        } else if (StrUtil.isNotBlank(finalText) && !StrUtil.equals(finalText, emitted)) {
            eventSink.onAssistantDelta(finalText);
        }

        LlmResult result = new LlmResult();
        result.setAssistantMessage(assistantMessage);
        result.setNdjson(ChatMessage.toNdjson(agentSession.getMessages()));
        result.setStreamed(true);
        result.setRawResponse(finalText);
        result.setProvider(resolved.getProvider());
        result.setModel(StrUtil.blankToDefault(resolved.getModel(), ""));
        if (finalResponse[0] != null) {
            applyMetrics(result, finalResponse[0].getMetrics());
        }
        logUsage(session, resolved, result);
        return result;
    }

    private void handleStreamChunk(org.noear.solon.ai.agent.AgentChunk chunk,
                                   StringBuilder emittedText,
                                   ConversationEventSink eventSink,
                                   ReActResponse[] finalResponse) {
        if (chunk instanceof org.noear.solon.ai.agent.react.task.ReasonChunk) {
            org.noear.solon.ai.agent.react.task.ReasonChunk reasonChunk = (org.noear.solon.ai.agent.react.task.ReasonChunk) chunk;
            if (reasonChunk.isToolCalls()) {
                return;
            }

            ChatMessage message = reasonChunk.getMessage();
            String delta = message == null ? null : message.getContent();
            if (StrUtil.isBlank(delta)) {
                return;
            }

            emittedText.append(delta);
            eventSink.onAssistantDelta(delta);
            return;
        }

        if (chunk instanceof org.noear.solon.ai.agent.react.task.ActionStartChunk) {
            org.noear.solon.ai.agent.react.task.ActionStartChunk actionChunk = (org.noear.solon.ai.agent.react.task.ActionStartChunk) chunk;
            eventSink.onToolStarted(actionChunk.getToolName(), actionChunk.getArgs());
            return;
        }

        if (chunk instanceof org.noear.solon.ai.agent.react.task.ActionEndChunk) {
            org.noear.solon.ai.agent.react.task.ActionEndChunk actionChunk = (org.noear.solon.ai.agent.react.task.ActionEndChunk) chunk;
            String preview = ToolPreviewSupport.buildPreview(actionChunk.getToolName(), actionChunk.getArgs(), 80, false);
            eventSink.onToolCompleted(actionChunk.getToolName(), preview, 0L);
            return;
        }

        if (chunk instanceof org.noear.solon.ai.agent.react.ReActChunk) {
            finalResponse[0] = ((org.noear.solon.ai.agent.react.ReActChunk) chunk).getResponse();
        }
    }

    private List<AppConfig.LlmConfig> buildCandidateConfigs(SessionRecord session) {
        List<AppConfig.LlmConfig> candidates = new java.util.ArrayList<AppConfig.LlmConfig>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<String>();

        if (appConfig.getProviders() == null
                || appConfig.getProviders().isEmpty()
                || StrUtil.isBlank(appConfig.getModel().getProviderKey())) {
            candidates.add(copyLlmConfig(appConfig.getLlm()));
            return candidates;
        }

        AppConfig.LlmConfig primary = toLlmConfig(llmProviderService.resolveEffectiveProvider(session));
        candidates.add(primary);
        seen.add(providerSignature(primary));

        for (LlmProviderService.ResolvedProvider fallback : llmProviderService.resolveFallbackProviders()) {
            AppConfig.LlmConfig candidate = toLlmConfig(fallback);
            String signature = providerSignature(candidate);
            if (seen.add(signature)) {
                candidates.add(candidate);
            }
        }

        return candidates;
    }

    private AppConfig.LlmConfig toLlmConfig(LlmProviderService.ResolvedProvider resolved) {
        AppConfig.LlmConfig config = copyLlmConfig(appConfig.getLlm());
        config.setProvider(StrUtil.nullToEmpty(resolved.getProviderKey()).trim());
        config.setDialect(StrUtil.nullToEmpty(resolved.getDialect()).trim());
        config.setApiUrl(StrUtil.nullToEmpty(resolved.getApiUrl()).trim());
        config.setApiKey(resolved.getApiKey());
        config.setModel(StrUtil.nullToEmpty(resolved.getModel()).trim());
        return config;
    }

    private boolean hasVisibleContent(AssistantMessage assistantMessage, String rawResponse) {
        return StrUtil.isNotBlank(extractText(assistantMessage)) || StrUtil.isNotBlank(rawResponse);
    }

    private String extractText(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return "";
        }
        if (StrUtil.isNotBlank(assistantMessage.getResultContent())) {
            return assistantMessage.getResultContent().trim();
        }
        if (StrUtil.isNotBlank(assistantMessage.getContent())) {
            return assistantMessage.getContent().trim();
        }
        return String.valueOf(assistantMessage).trim();
    }

    private FailureMode classifyFailure(Throwable error) {
        String message = collectErrorText(error).toLowerCase(Locale.ROOT);
        int status = extractStatusCode(message);
        if (status == 401 || status == 403 || status == 404) {
            return FailureMode.FALLBACK_NOW;
        }
        if (status == 429 || status == 500 || status == 502 || status == 503) {
            return FailureMode.RETRY_THEN_FALLBACK;
        }
        if (message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection reset")
                || message.contains("connection refused")
                || message.contains("connection aborted")
                || message.contains("broken pipe")
                || message.contains("eof")
                || message.contains("unreachable")
                || message.contains("network")) {
            return FailureMode.RETRY_THEN_FALLBACK;
        }
        return FailureMode.FALLBACK_NOW;
    }

    private String collectErrorText(Throwable error) {
        StringBuilder buffer = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().trim().length() > 0) {
                if (buffer.length() > 0) {
                    buffer.append(" | ");
                }
                buffer.append(current.getMessage());
            }
            current = current.getCause();
        }
        return buffer.toString();
    }

    private int extractStatusCode(String message) {
        String[] candidates = {"401", "403", "404", "429", "500", "502", "503"};
        for (String candidate : candidates) {
            if (message.contains(" " + candidate + " ")
                    || message.contains("http " + candidate)
                    || message.contains("status=" + candidate)
                    || message.contains("code=" + candidate)
                    || message.contains("[" + candidate + "]")
                    || message.endsWith(candidate)) {
                return Integer.parseInt(candidate);
            }
        }
        return 0;
    }

    /**
     * 校验 provider 与 URL 配置，避免隐式降级到错误协议。
     */
    private void validate(AppConfig.LlmConfig resolved) {
        if (StrUtil.isBlank(resolved.getProvider())) {
            throw new IllegalStateException("LLM provider 不能为空。");
        }
        String dialect = LlmProviderSupport.normalizeDialect(StrUtil.isNotBlank(resolved.getDialect()) ? resolved.getDialect() : resolved.getProvider());
        if (!LlmConstants.SUPPORTED_PROVIDERS.contains(dialect)) {
            throw new IllegalStateException("不支持的 provider dialect：" + dialect);
        }
        if (StrUtil.isBlank(resolved.getApiUrl())) {
            throw new IllegalStateException("LLM apiUrl 不能为空。");
        }
        if (StrUtil.isBlank(resolved.getModel())) {
            throw new IllegalStateException("LLM model 不能为空。");
        }
        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect)
                && !StrUtil.containsIgnoreCase(resolved.getApiUrl(), "/responses")) {
            throw new IllegalStateException("openai-responses 的 apiUrl 必须直接指向 /responses 接口。");
        }
    }

    private ChatConfig buildChatConfig(AppConfig.LlmConfig resolved) {
        ensureCustomDialectsRegistered();
        String dialect = LlmProviderSupport.normalizeDialect(StrUtil.isNotBlank(resolved.getDialect()) ? resolved.getDialect() : resolved.getProvider());

        ChatConfig chatConfig = new ChatConfig();
        chatConfig.setApiUrl(resolved.getApiUrl());
        chatConfig.setProvider(dialect);
        chatConfig.setModel(resolved.getModel());
        chatConfig.setTimeout(Duration.ofMinutes(5));

        if (resolved.getApiKey() != null && resolved.getApiKey().trim().length() > 0) {
            chatConfig.setApiKey(resolved.getApiKey());
        }

        chatConfig.getModelOptions().temperature(resolved.getTemperature());
        chatConfig.getModelOptions().max_tokens(resolved.getMaxTokens());
        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(dialect)
                && resolved.getReasoningEffort() != null && resolved.getReasoningEffort().trim().length() > 0) {
            chatConfig.getModelOptions().optionSet("reasoning", Collections.<String, Object>singletonMap("effort", resolved.getReasoningEffort()));
        }

        return chatConfig;
    }

    private ChatModel buildChatModel(AppConfig.LlmConfig resolved) {
        return buildChatConfig(resolved).toChatModel();
    }

    private String providerSignature(AppConfig.LlmConfig config) {
        return StrUtil.nullToEmpty(config.getProvider())
                + "|" + StrUtil.nullToEmpty(config.getDialect())
                + "|" + StrUtil.nullToEmpty(config.getApiUrl())
                + "|" + StrUtil.nullToEmpty(config.getModel())
                + "|" + (StrUtil.isBlank(config.getApiKey()) ? "no-key" : "has-key");
    }

    private void ensureCustomDialectsRegistered() {
        if (CUSTOM_DIALECTS_REGISTERED.compareAndSet(false, true)) {
            ChatDialectManager.register(new LoggingOpenaiChatDialect(), -100);
            ChatDialectManager.register(new LoggingOpenaiResponsesDialect(), -100);
        }
    }

    private ReActAgent buildHarnessReActAgent(ChatConfig chatConfig,
                                              AppConfig.LlmConfig resolved,
                                              String systemPrompt,
                                              List<Object> toolObjects,
                                              SqliteAgentSession agentSession,
                                              ConversationFeedbackSink feedbackSink,
                                              AgentRunContext runContext) {
        boolean delegateSession = isDelegateSession(agentSession);
        int maxSteps = delegateSession ? appConfig.getReact().getDelegateMaxSteps() : appConfig.getReact().getMaxSteps();
        int retryMax = delegateSession ? appConfig.getReact().getDelegateRetryMax() : appConfig.getReact().getRetryMax();
        long retryDelayMs = delegateSession ? appConfig.getReact().getDelegateRetryDelayMs() : appConfig.getReact().getRetryDelayMs();

        HarnessProperties harnessProperties = new HarnessProperties(".jimuqu-harness");
        harnessProperties.setWorkspace(appConfig.getRuntime().getHome());
        harnessProperties.addModel(chatConfig);
        harnessProperties.setMaxSteps(maxSteps);
        harnessProperties.setMaxStepsAutoExtensible(false);
        harnessProperties.setSessionWindowSize(Math.max(8, agentSession.getMessages().size() + 8));
        harnessProperties.setSummaryWindowSize(Math.max(10, appConfig.getReact().getSummarizationMaxMessages()));
        harnessProperties.setSummaryWindowToken(Math.max(8000, appConfig.getReact().getSummarizationMaxTokens()));
        harnessProperties.setSandboxMode(true);
        harnessProperties.setSubagentEnabled(false);
        harnessProperties.setHitlEnabled(false);

        HarnessEngine harnessEngine = HarnessEngine.of(harnessProperties)
                .sessionProvider(new FixedAgentSessionProvider(agentSession))
                .summarizationInterceptor(buildHarnessSummarizationInterceptor(resolved, chatConfig))
                .build();

        AgentDefinition definition = new AgentDefinition();
        definition.setSystemPrompt(systemPrompt);
        definition.getMetadata().setName("jimuqu_react");
        definition.getMetadata().setDescription("Jimuqu Agent");
        definition.getMetadata().setMaxSteps(maxSteps);
        definition.getMetadata().setMaxStepsAutoExtensible(Boolean.FALSE);
        definition.getMetadata().setSessionWindowSize(Math.max(8, agentSession.getMessages().size() + 8));
        definition.getMetadata().setTools(Collections.<String>emptyList());

        ReActAgent.Builder builder = harnessEngine.createSubagent(definition)
                .role("Jimuqu Agent")
                .retryConfig(retryMax, retryDelayMs)
                .defaultInterceptorAdd(new ToolRetryInterceptor())
                .defaultInterceptorAdd(new ToolSanitizerInterceptor());
        if (dangerousCommandApprovalService != null) {
            builder.defaultInterceptorAdd(dangerousCommandApprovalService.buildInterceptor());
        }
        if (feedbackSink != null && feedbackSink != ConversationFeedbackSink.noop()) {
            builder.defaultInterceptorAdd(new FeedbackInterceptor(feedbackSink, dangerousCommandApprovalService));
        }
        if (runContext != null) {
            builder.defaultInterceptorAdd(new TracingReActInterceptor(runContext, appConfig.getTrace().getToolPreviewLength()));
        }

        for (Object toolObject : toolObjects) {
            builder.defaultToolAdd(toolObject);
        }
        builder.defaultSkillAdd(pdfSkill());
        return builder.build();
    }

    private SummarizationInterceptor buildHarnessSummarizationInterceptor(AppConfig.LlmConfig resolved, ChatConfig chatConfig) {
        if (!appConfig.getReact().isSummarizationEnabled()) {
            return new NoopSummarizationInterceptor();
        }

        ChatModel summaryChatModel = buildSummaryChatModel(resolved, chatConfig);
        CompositeSummarizationStrategy strategy = new CompositeSummarizationStrategy()
                .addStrategy(new KeyInfoExtractionStrategy(summaryChatModel))
                .addStrategy(new HierarchicalSummarizationStrategy(summaryChatModel));

        return new SummarizationInterceptor(
                Math.max(10, appConfig.getReact().getSummarizationMaxMessages()),
                Math.max(8000, appConfig.getReact().getSummarizationMaxTokens()),
                strategy
        );
    }

    private ChatModel buildSummaryChatModel(AppConfig.LlmConfig resolved, ChatConfig chatConfig) {
        String summaryModel = StrUtil.nullToEmpty(appConfig.getCompression().getSummaryModel()).trim();
        if (StrUtil.isBlank(summaryModel) || StrUtil.equals(summaryModel, resolved.getModel())) {
            return chatConfig.toChatModel();
        }

        AppConfig.LlmConfig summaryConfig = copyLlmConfig(resolved);
        summaryConfig.setModel(summaryModel);
        return buildChatConfig(summaryConfig).toChatModel();
    }

    private AppConfig.LlmConfig copyLlmConfig(AppConfig.LlmConfig source) {
        AppConfig.LlmConfig copy = new AppConfig.LlmConfig();
        copy.setProvider(source.getProvider());
        copy.setDialect(source.getDialect());
        copy.setApiUrl(source.getApiUrl());
        copy.setApiKey(source.getApiKey());
        copy.setModel(source.getModel());
        copy.setStream(source.isStream());
        copy.setReasoningEffort(source.getReasoningEffort());
        copy.setTemperature(source.getTemperature());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setContextWindowTokens(source.getContextWindowTokens());
        return copy;
    }

    private void applyMetrics(LlmResult result, Metrics metrics) {
        if (metrics == null) {
            return;
        }
        result.setInputTokens(metrics.getPromptTokens());
        result.setOutputTokens(metrics.getCompletionTokens());
        result.setTotalTokens(metrics.getTotalTokens());
    }

    private void logUsage(SessionRecord session, AppConfig.LlmConfig resolved, LlmResult result) {
        if (result.getTotalTokens() <= 0 && result.getInputTokens() <= 0 && result.getOutputTokens() <= 0) {
            log.info("LLM usage unavailable: provider={}, dialect={}, model={}, sessionId={}",
                    resolved.getProvider(),
                    resolved.getDialect(),
                    resolved.getModel(),
                    session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()));
            return;
        }

        log.info("LLM usage: provider={}, dialect={}, model={}, sessionId={}, inputTokens={}, outputTokens={}, totalTokens={}",
                resolved.getProvider(),
                resolved.getDialect(),
                resolved.getModel(),
                session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()),
                result.getInputTokens(),
                result.getOutputTokens(),
                result.getTotalTokens());
    }

    private enum FailureMode {
        RETRY_THEN_FALLBACK(true),
        FALLBACK_NOW(false);

        private final boolean retryable;

        FailureMode(boolean retryable) {
            this.retryable = retryable;
        }
    }

    private boolean isDelegateSession(SqliteAgentSession agentSession) {
        Object sourceKey = agentSession.getContext().get("source_key");
        if (sourceKey != null && String.valueOf(sourceKey).contains(":delegate:")) {
            return true;
        }
        Object parentSessionId = agentSession.getContext().get("parent_session_id");
        if (parentSessionId != null && StrUtil.isNotBlank(String.valueOf(parentSessionId))) {
            return true;
        }
        return false;
    }

    /**
     * Harness 需要一个会话提供器；Jimuqu 的会话生命周期仍由外层仓储控制。
     */
    private static class FixedAgentSessionProvider implements AgentSessionProvider {
        private final AgentSession session;

        private FixedAgentSessionProvider(AgentSession session) {
            this.session = session;
        }

        @Override
        public AgentSession getSession(String instanceId) {
            return session;
        }
    }

    /**
     * 关闭 Jimuqu 配置里的 ReAct 摘要时，避免 Harness 默认摘要拦截器改变现有行为。
     */
    private static class NoopSummarizationInterceptor extends SummarizationInterceptor {
        @Override
        public void onObservation(ReActTrace trace, String toolName, String result, long durationMs) {
            // no-op
        }
    }

    /**
     * 懒加载 PDF 技能，统一复用 runtime/cache/pdf 目录。
     */
    PdfSkill pdfSkill() {
        if (pdfSkill == null) {
            synchronized (this) {
                if (pdfSkill == null) {
                    pdfSkill = buildPdfSkill();
                }
            }
        }
        return pdfSkill;
    }

    private PdfSkill buildPdfSkill() {
        File pdfWorkDir = new File(appConfig.getRuntime().getCacheDir(), "pdf");
        if (!pdfWorkDir.exists() && !pdfWorkDir.mkdirs()) {
            log.warn("Failed to create pdf work directory: {}", pdfWorkDir.getAbsolutePath());
        }

        final File fontFile = resolvePdfFontFile();
        if (fontFile != null) {
            log.info("PDF skill font detected: {}", fontFile.getAbsolutePath());
            return new PdfSkill(pdfWorkDir.getAbsolutePath(), new Supplier<InputStream>() {
                @Override
                public InputStream get() {
                    try {
                        return new FileInputStream(fontFile);
                    } catch (Exception e) {
                        log.warn("Failed to open PDF font file: {}", fontFile.getAbsolutePath(), e);
                        return null;
                    }
                }
            });
        }

        log.warn("No PDF font detected, PDF generation will use default font fallback");
        return new PdfSkill(pdfWorkDir.getAbsolutePath());
    }

    private File resolvePdfFontFile() {
        String override = RuntimeConfigResolver.getValue("jimuqu.pdf.fontPath");
        if (StrUtil.isNotBlank(override)) {
            File file = new File(override.trim());
            if (file.isFile()) {
                return file;
            }
            log.warn("Configured PDF font path not found: {}", file.getAbsolutePath());
        }

        List<String> candidates = Arrays.asList(
                "C:\\Windows\\Fonts\\msyh.ttf",
                "C:\\Windows\\Fonts\\simhei.ttf",
                "/Library/Fonts/Arial Unicode.ttf",
                "/usr/share/fonts/truetype/arphic-gbsn00lp/gbsn00lp.ttf",
                "/usr/share/fonts/truetype/arphic/gbsn00lp.ttf",
                "/usr/share/fonts/truetype/gbsn00lp.ttf",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttf",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"
        );

        for (String path : candidates) {
            File file = new File(path);
            if (file.isFile()) {
                return file;
            }
        }

        return null;
    }

    /**
     * 将 ReAct 生命周期事件桥接到网关反馈 sink。
     */
    private static class FeedbackInterceptor implements ReActInterceptor {
        private final ConversationFeedbackSink feedbackSink;
        private final DangerousCommandApprovalService dangerousCommandApprovalService;

        private FeedbackInterceptor(ConversationFeedbackSink feedbackSink,
                                    DangerousCommandApprovalService dangerousCommandApprovalService) {
            this.feedbackSink = feedbackSink;
            this.dangerousCommandApprovalService = dangerousCommandApprovalService;
        }

        @Override
        public void onThought(ReActTrace trace, String thought) {
            feedbackSink.onReasoning(thought);
        }

        @Override
        public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
            if (trace != null && trace.getSession() != null && trace.getSession().isPending()) {
                return;
            }
            if (dangerousCommandApprovalService != null
                    && trace != null
                    && trace.getSession() != null
                    && dangerousCommandApprovalService.getPendingApproval(trace.getSession()) != null) {
                return;
            }
            feedbackSink.onToolStarted(toolName, args);
        }

        @Override
        public void onObservation(ReActTrace trace, String toolName, String result, long durationMs) {
            feedbackSink.onToolFinished(toolName, result, durationMs);
        }
    }

    /**
     * 将 ReAct 生命周期写入持久化 run 轨迹。
     */
    private static class TracingReActInterceptor implements ReActInterceptor {
        private final AgentRunContext runContext;
        private final int previewLength;

        private TracingReActInterceptor(AgentRunContext runContext, int previewLength) {
            this.runContext = runContext;
            this.previewLength = Math.max(200, previewLength);
        }

        @Override
        public void onModelStart(ReActTrace trace, ChatRequestDesc req) {
            runContext.event("model.start", "开始请求模型");
        }

        @Override
        public void onModelEnd(ReActTrace trace, ChatResponse resp) {
            runContext.event("model.end", "模型响应完成");
        }

        @Override
        public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
            Map<String, Object> metadata = new java.util.LinkedHashMap<String, Object>();
            metadata.put("tool", toolName);
            metadata.put("args", args);
            runContext.event("tool.start", "调用工具：" + toolName, metadata);
        }

        @Override
        public void onObservation(ReActTrace trace, String toolName, String result, long durationMs) {
            Map<String, Object> metadata = new java.util.LinkedHashMap<String, Object>();
            metadata.put("tool", toolName);
            metadata.put("durationMs", durationMs);
            metadata.put("preview", AgentRunContext.safe(result, previewLength));
            runContext.event("tool.end", "工具完成：" + toolName + "（" + durationMs + "ms）", metadata);
        }
    }
}
