package com.jimuqu.agent.llm;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.config.RuntimeEnvResolver;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.gateway.feedback.ConversationFeedbackSink;
import com.jimuqu.agent.llm.dialect.LoggingOpenaiResponsesDialect;
import com.jimuqu.agent.storage.session.SqliteAgentSession;
import com.jimuqu.agent.support.constants.LlmConstants;
import com.jimuqu.agent.tool.runtime.DangerousCommandApprovalService;
import org.noear.solon.ai.agent.trace.Metrics;
import org.noear.solon.ai.agent.AgentSystemPrompt;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActResponse;
import org.noear.solon.ai.agent.react.ReActTrace;
import org.noear.solon.ai.agent.react.intercept.StopLoopInterceptor;
import org.noear.solon.ai.agent.react.intercept.SummarizationInterceptor;
import org.noear.solon.ai.agent.react.intercept.ToolRetryInterceptor;
import org.noear.solon.ai.agent.react.intercept.ToolSanitizerInterceptor;
import org.noear.solon.ai.agent.react.intercept.summarize.CompositeSummarizationStrategy;
import org.noear.solon.ai.agent.react.intercept.summarize.HierarchicalSummarizationStrategy;
import org.noear.solon.ai.agent.react.intercept.summarize.KeyInfoExtractionStrategy;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.dialect.ChatDialectManager;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.prompt.Prompt;
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
    private static final AtomicBoolean OPENAI_RESPONSES_DIALECT_REGISTERED = new AtomicBoolean(false);

    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final DangerousCommandApprovalService dangerousCommandApprovalService;
    private volatile PdfSkill pdfSkill;

    public SolonAiLlmGateway(AppConfig appConfig) {
        this(appConfig, null, null);
    }

    public SolonAiLlmGateway(AppConfig appConfig, SessionRepository sessionRepository) {
        this(appConfig, sessionRepository, null);
    }

    public SolonAiLlmGateway(AppConfig appConfig,
                             SessionRepository sessionRepository,
                             DangerousCommandApprovalService dangerousCommandApprovalService) {
        this.appConfig = appConfig;
        this.sessionRepository = sessionRepository;
        this.dangerousCommandApprovalService = dangerousCommandApprovalService;
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
        AppConfig.LlmConfig resolved = resolve(session);
        validate(resolved);
        log.info("LLM request: provider={}, model={}, sessionId={}, stream={}, sessionOverride={}",
                resolved.getProvider(),
                resolved.getModel(),
                session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()),
                resolved.isStream(),
                session != null && StrUtil.isNotBlank(session.getModelOverride()));
        SqliteAgentSession agentSession = new SqliteAgentSession(session, sessionRepository);
        ChatModel chatModel = buildChatModel(resolved);
        ReActAgent agent = buildReActAgent(chatModel, resolved, systemPrompt, toolObjects, agentSession, feedbackSink);
        ReActResponse response = callAgent(agent, agentSession, userMessage, false);

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

    @Override
    public LlmResult resume(SessionRecord session, String systemPrompt, List<Object> toolObjects) throws Exception {
        return resume(session, systemPrompt, toolObjects, ConversationFeedbackSink.noop());
    }

    @Override
    public LlmResult resume(SessionRecord session,
                            String systemPrompt,
                            List<Object> toolObjects,
                            ConversationFeedbackSink feedbackSink) throws Exception {
        AppConfig.LlmConfig resolved = resolve(session);
        validate(resolved);
        log.info("LLM resume: provider={}, model={}, sessionId={}, stream={}, sessionOverride={}",
                resolved.getProvider(),
                resolved.getModel(),
                session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()),
                resolved.isStream(),
                session != null && StrUtil.isNotBlank(session.getModelOverride()));
        SqliteAgentSession agentSession = new SqliteAgentSession(session, sessionRepository);
        ChatModel chatModel = buildChatModel(resolved);
        ReActAgent agent = buildReActAgent(chatModel, resolved, systemPrompt, toolObjects, agentSession, feedbackSink);
        ReActResponse response = callAgent(agent, agentSession, null, true);

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

    private ChatModel buildChatModel(AppConfig.LlmConfig resolved) {
        ensureCustomDialectsRegistered();

        ChatModel.Builder builder = ChatModel.of(resolved.getApiUrl())
                .provider(resolved.getProvider())
                .model(resolved.getModel())
                .timeout(Duration.ofMinutes(5));

        if (resolved.getApiKey() != null && resolved.getApiKey().trim().length() > 0) {
            builder.apiKey(resolved.getApiKey());
        }

        builder.modelOptions(options -> {
            options.temperature(resolved.getTemperature());
            options.max_tokens(resolved.getMaxTokens());
            if (resolved.getReasoningEffort() != null && resolved.getReasoningEffort().trim().length() > 0) {
                options.optionSet("reasoning", java.util.Collections.<String, Object>singletonMap("effort", resolved.getReasoningEffort()));
            }
        });

        return builder.build();
    }

    private void ensureCustomDialectsRegistered() {
        if (OPENAI_RESPONSES_DIALECT_REGISTERED.compareAndSet(false, true)) {
            ChatDialectManager.register(new LoggingOpenaiResponsesDialect(), -100);
        }
    }

    private ReActAgent buildReActAgent(ChatModel chatModel,
                                       AppConfig.LlmConfig resolved,
                                       final String systemPrompt,
                                       List<Object> toolObjects,
                                       SqliteAgentSession agentSession,
                                       ConversationFeedbackSink feedbackSink) {
        boolean delegateSession = isDelegateSession(agentSession);
        int maxSteps = delegateSession ? appConfig.getReact().getDelegateMaxSteps() : appConfig.getReact().getMaxSteps();
        int retryMax = delegateSession ? appConfig.getReact().getDelegateRetryMax() : appConfig.getReact().getRetryMax();
        long retryDelayMs = delegateSession ? appConfig.getReact().getDelegateRetryDelayMs() : appConfig.getReact().getRetryDelayMs();
        SummarizationInterceptor summarizationInterceptor = buildSummarizationInterceptor(resolved, chatModel);
        ReActAgent.Builder builder = ReActAgent.of(chatModel)
                .name("jimuqu_react")
                .role("Jimuqu Agent")
                .systemPrompt(new AgentSystemPrompt<org.noear.solon.ai.agent.react.ReActTrace>() {
                    @Override
                    public Locale getLocale() {
                        return Locale.CHINESE;
                    }

                    @Override
                    public String getSystemPrompt(org.noear.solon.ai.agent.react.ReActTrace trace) {
                        return systemPrompt;
                    }
                })
                .sessionWindowSize(Math.max(8, agentSession.getMessages().size() + 8))
                .retryConfig(retryMax, retryDelayMs)
                .maxSteps(maxSteps)
                .defaultInterceptorAdd(new ToolRetryInterceptor())
                .defaultInterceptorAdd(new ToolSanitizerInterceptor())
                .defaultInterceptorAdd(new StopLoopInterceptor());

        if (summarizationInterceptor != null) {
            builder.defaultInterceptorAdd(summarizationInterceptor);
        }
        if (dangerousCommandApprovalService != null) {
            builder.defaultInterceptorAdd(dangerousCommandApprovalService.buildInterceptor());
        }
        if (feedbackSink != null && feedbackSink != ConversationFeedbackSink.noop()) {
            builder.defaultInterceptorAdd(new FeedbackInterceptor(feedbackSink, dangerousCommandApprovalService));
        }

        for (Object toolObject : toolObjects) {
            builder.defaultToolAdd(toolObject);
        }
        builder.defaultSkillAdd(pdfSkill());
        return builder.build();
    }

    private SummarizationInterceptor buildSummarizationInterceptor(AppConfig.LlmConfig resolved, ChatModel chatModel) {
        if (!appConfig.getReact().isSummarizationEnabled()) {
            return null;
        }

        ChatModel summaryChatModel = buildSummaryChatModel(resolved, chatModel);
        CompositeSummarizationStrategy strategy = new CompositeSummarizationStrategy()
                .addStrategy(new KeyInfoExtractionStrategy(summaryChatModel))
                .addStrategy(new HierarchicalSummarizationStrategy(summaryChatModel));

        return new SummarizationInterceptor(
                Math.max(10, appConfig.getReact().getSummarizationMaxMessages()),
                Math.max(8000, appConfig.getReact().getSummarizationMaxTokens()),
                strategy
        );
    }

    private ChatModel buildSummaryChatModel(AppConfig.LlmConfig resolved, ChatModel chatModel) {
        String summaryModel = StrUtil.nullToEmpty(appConfig.getCompression().getSummaryModel()).trim();
        if (StrUtil.isBlank(summaryModel) || StrUtil.equals(summaryModel, resolved.getModel())) {
            return chatModel;
        }

        AppConfig.LlmConfig summaryConfig = copyLlmConfig(resolved);
        summaryConfig.setModel(summaryModel);
        return buildChatModel(summaryConfig);
    }

    private AppConfig.LlmConfig copyLlmConfig(AppConfig.LlmConfig source) {
        AppConfig.LlmConfig copy = new AppConfig.LlmConfig();
        copy.setProvider(source.getProvider());
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
            log.info("LLM usage unavailable: provider={}, model={}, sessionId={}",
                    resolved.getProvider(),
                    resolved.getModel(),
                    session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()));
            return;
        }

        log.info("LLM usage: provider={}, model={}, sessionId={}, inputTokens={}, outputTokens={}, totalTokens={}",
                resolved.getProvider(),
                resolved.getModel(),
                session == null ? "" : StrUtil.nullToEmpty(session.getSessionId()),
                result.getInputTokens(),
                result.getOutputTokens(),
                result.getTotalTokens());
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
        String override = RuntimeEnvResolver.getenv("JIMUQU_PDF_FONT_PATH");
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
}
