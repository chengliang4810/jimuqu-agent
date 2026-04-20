package com.jimuqu.agent.llm;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.storage.session.SqliteAgentSession;
import com.jimuqu.agent.support.constants.LlmConstants;
import org.noear.solon.ai.agent.AgentSystemPrompt;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActResponse;
import org.noear.solon.ai.agent.react.intercept.StopLoopInterceptor;
import org.noear.solon.ai.agent.react.intercept.ToolRetryInterceptor;
import org.noear.solon.ai.agent.react.intercept.ToolSanitizerInterceptor;
import org.noear.solon.ai.chat.ChatModel;
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
import java.util.function.Supplier;

/**
 * SolonAiLlmGateway 实现。
 */
public class SolonAiLlmGateway implements LlmGateway {
    /**
     * LLM 网关日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(SolonAiLlmGateway.class);

    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private volatile PdfSkill pdfSkill;

    public SolonAiLlmGateway(AppConfig appConfig) {
        this(appConfig, null);
    }

    public SolonAiLlmGateway(AppConfig appConfig, SessionRepository sessionRepository) {
        this.appConfig = appConfig;
        this.sessionRepository = sessionRepository;
    }

    public LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception {
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
        ReActAgent agent = buildReActAgent(chatModel, systemPrompt, toolObjects, agentSession);
        ReActResponse response;
        try {
            response = agent.prompt(Prompt.of(userMessage))
                    .session(agentSession)
                    .options(options -> options.toolContextPut("user_message", userMessage))
                    .call();
        } catch (Exception e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("ReActAgent call failed", e);
        }

        AssistantMessage assistantMessage = response.getMessage();

        LlmResult result = new LlmResult();
        result.setAssistantMessage(assistantMessage);
        result.setNdjson(ChatMessage.toNdjson(agentSession.getMessages()));
        result.setStreamed(resolved.isStream());
        result.setRawResponse(response.getContent());
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

    private ReActAgent buildReActAgent(ChatModel chatModel,
                                       final String systemPrompt,
                                       List<Object> toolObjects,
                                       SqliteAgentSession agentSession) {
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
                .defaultInterceptorAdd(new ToolRetryInterceptor())
                .defaultInterceptorAdd(new ToolSanitizerInterceptor())
                .defaultInterceptorAdd(new StopLoopInterceptor());

        for (Object toolObject : toolObjects) {
            builder.defaultToolAdd(toolObject);
        }
        builder.defaultSkillAdd(pdfSkill());
        return builder.build();
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
        String override = System.getenv("JIMUQU_PDF_FONT_PATH");
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
}
