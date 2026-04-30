package com.jimuqu.solon.claw.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.agent.AgentRuntimeScope;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.AgentRunContext;
import com.jimuqu.solon.claw.core.model.AgentRunOutcome;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.AgentRunStopResult;
import com.jimuqu.solon.claw.core.model.CompressionOutcome;
import com.jimuqu.solon.claw.core.model.ContextBudgetDecision;
import com.jimuqu.solon.claw.core.model.LlmResult;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.AgentRunCancelledException;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.core.service.ContextBudgetService;
import com.jimuqu.solon.claw.core.service.ContextCompressionService;
import com.jimuqu.solon.claw.core.service.ConversationEventSink;
import com.jimuqu.solon.claw.core.service.LlmGateway;
import com.jimuqu.solon.claw.gateway.feedback.ConversationFeedbackSink;
import com.jimuqu.solon.claw.llm.LlmErrorClassifier;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.LlmProviderService;
import com.jimuqu.solon.claw.support.MessageSupport;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** OpenClaw 风格的外层 Agent run 状态机。 */
@RequiredArgsConstructor
public class AgentRunSupervisor implements AgentRunControlService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunSupervisor.class);
    private static final String EMPTY_REPLY_RECOVERY_PROMPT =
            "你刚刚已经完成了工具调用，但没有输出最终答复。请基于当前会话中的最新工具结果，直接用中文给出简洁最终答复，不要再次调用工具。";
    private static final String EMPTY_REPLY_FALLBACK =
            "本轮已完成工具调用，但模型没有返回可读结论。请使用 /retry 重试，或继续给出下一步指令。";
    private static final String MAX_STEPS_RECOVERY_PROMPT =
            "你刚刚因为最大推理步数限制而停止。不要再次调用工具。请基于当前会话中已经完成的分析、工具结果、文件修改和观察，直接输出中文收敛答复：优先给出已经完成的结果；若任务仍未彻底完成，明确说明还差什么、最推荐的下一步是什么。";
    private static final String MAX_STEPS_RECOVERY_FALLBACK =
            "本轮执行已达到最大步骤限制，已保留当前进展。请继续给出更聚焦的下一步，或使用 /retry 继续。";

    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final AgentRunRepository agentRunRepository;
    private final ContextCompressionService contextCompressionService;
    private final ContextBudgetService contextBudgetService;
    private final LlmGateway llmGateway;
    private final LlmProviderService llmProviderService;
    private final ConcurrentMap<String, RunHandle> runningRuns =
            new ConcurrentHashMap<String, RunHandle>();
    private volatile long lastRunFinishedAt;

    @Override
    public AgentRunStopResult stop(String sourceKey) {
        RunHandle handle = runningRuns.get(normalizeSourceKey(sourceKey));
        if (handle == null) {
            return AgentRunStopResult.none();
        }
        handle.cancelled.set(true);
        Thread thread = handle.thread;
        boolean interruptSent = false;
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            interruptSent = true;
        }
        return AgentRunStopResult.stopped(
                handle.runId, handle.sessionId, interruptSent, handle.startedAt);
    }

    @Override
    public boolean isRunning(String sourceKey) {
        RunHandle handle = runningRuns.get(normalizeSourceKey(sourceKey));
        return handle != null && !handle.cancelled.get();
    }

    @Override
    public boolean hasRunningRuns() {
        return !runningRuns.isEmpty();
    }

    @Override
    public long lastRunFinishedAt() {
        return lastRunFinishedAt;
    }

    public AgentRunOutcome run(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> tools,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume)
            throws Exception {
        return run(
                session, systemPrompt, userMessage, tools, feedbackSink, eventSink, resume, null);
    }

    public AgentRunOutcome run(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            List<Object> tools,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            boolean resume,
            AgentRuntimeScope agentScope)
            throws Exception {
        if (agentScope == null) {
            agentScope = new AgentRuntimeScope();
            agentScope.setAgentName(
                    AgentRuntimeScope.normalizeName(
                            session == null ? null : session.getActiveAgentName()));
            agentScope.setWorkspaceDir(appConfig.getRuntime().getHome());
            agentScope.setSkillsDir(appConfig.getRuntime().getSkillsDir());
            agentScope.setCacheDir(appConfig.getRuntime().getCacheDir());
        }
        long now = System.currentTimeMillis();
        AgentRunRecord runRecord = new AgentRunRecord();
        runRecord.setRunId(IdSupport.newId());
        runRecord.setSessionId(session.getSessionId());
        runRecord.setSourceKey(session.getSourceKey());
        runRecord.setAgentName(agentScope.getEffectiveName());
        runRecord.setAgentSnapshotJson(agentScope.getSnapshotJson());
        runRecord.setStatus("running");
        runRecord.setInputPreview(AgentRunContext.safe(userMessage, 1000));
        runRecord.setStartedAt(now);
        agentRunRepository.saveRun(runRecord);

        AgentRunContext runContext =
                new AgentRunContext(
                        agentRunRepository,
                        runRecord.getRunId(),
                        session.getSessionId(),
                        session.getSourceKey());
        runContext.setWorkspaceDir(agentScope.getWorkspaceDir());
        RunHandle runHandle =
                registerRun(
                        session.getSourceKey(), runRecord.getRunId(), session.getSessionId(), now);
        try {
            pruneOldRuns();

            runContext.event("run.start", resume ? "恢复挂起会话" : "开始执行用户请求");
            eventSink.onRunStarted(session.getSessionId());

            List<AppConfig.LlmConfig> candidates = buildCandidateConfigs(session, agentScope);
            Throwable lastError = null;
            LlmResult finalResult = null;
            String replyText = "";
            String previousProvider = null;
            int attemptNo = 0;
            String compressionWarning = "";
            int contextEstimateTokens = 0;
            int contextWindowTokens = 0;

            for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
                checkCancellation(session.getSourceKey());
                AppConfig.LlmConfig resolved = candidates.get(candidateIndex);
                int maxAttempts = Math.max(1, appConfig.getTrace().getMaxAttempts());
                for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                    checkCancellation(session.getSourceKey());
                    attemptNo++;
                    runContext.setAttempt(attemptNo, resolved.getProvider(), resolved.getModel());
                    runRecord.setAttempts(attemptNo);
                    runRecord.setProvider(resolved.getProvider());
                    runRecord.setModel(resolved.getModel());
                    agentRunRepository.saveRun(runRecord);
                    eventSink.onAttemptStarted(
                            runRecord.getRunId(),
                            attemptNo,
                            resolved.getProvider(),
                            resolved.getModel());
                    runContext.event(
                            "attempt.start",
                            "开始第 "
                                    + attemptNo
                                    + " 次尝试："
                                    + resolved.getProvider()
                                    + "/"
                                    + resolved.getModel());

                    try {
                        CompressionOutcome compression =
                                compressBeforeAttempt(
                                        session,
                                        systemPrompt,
                                        userMessage,
                                        resolved,
                                        runContext,
                                        eventSink,
                                        runRecord.getRunId());
                        session = compression.getSession();
                        if (StrUtil.isBlank(compressionWarning)
                                && StrUtil.isNotBlank(compression.getWarning())) {
                            compressionWarning = compression.getWarning();
                        }
                        if (compression.getEstimatedTokens() > 0) {
                            contextEstimateTokens = compression.getEstimatedTokens();
                        }
                        contextWindowTokens = Math.max(1024, resolved.getContextWindowTokens());
                        checkCancellation(session.getSourceKey());
                        String previousNdjson = session.getNdjson();
                        LlmResult result =
                                llmGateway.executeOnce(
                                        session,
                                        systemPrompt,
                                        userMessage,
                                        tools,
                                        feedbackSink,
                                        eventSink,
                                        resume,
                                        resolved,
                                        runContext);
                        checkCancellation(session.getSourceKey());
                        String currentReply = extractText(result.getAssistantMessage());
                        if (StrUtil.isBlank(currentReply)
                                && hasRecentToolActivity(previousNdjson, result.getNdjson())) {
                            session.setNdjson(result.getNdjson());
                            checkCancellation(session.getSourceKey());
                            runContext.event("recovery.start", "工具调用后空回复，发起无工具恢复");
                            eventSink.onRecoveryStarted(runRecord.getRunId(), "empty_reply");
                            LlmResult recovered =
                                    recover(
                                            session,
                                            systemPrompt,
                                            EMPTY_REPLY_RECOVERY_PROMPT,
                                            resolved,
                                            feedbackSink,
                                            eventSink,
                                            runContext);
                            checkCancellation(session.getSourceKey());
                            if (recovered != null) {
                                mergeUsage(result, recovered);
                                result = recovered;
                                currentReply = extractText(recovered.getAssistantMessage());
                            }
                        }

                        if (isMaxStepsReply(currentReply)) {
                            session.setNdjson(result.getNdjson());
                            checkCancellation(session.getSourceKey());
                            runContext.event("recovery.start", "达到最大步骤上限，发起收敛总结");
                            eventSink.onRecoveryStarted(runRecord.getRunId(), "max_steps");
                            LlmResult recovered =
                                    recover(
                                            session,
                                            systemPrompt,
                                            MAX_STEPS_RECOVERY_PROMPT,
                                            resolved,
                                            feedbackSink,
                                            eventSink,
                                            runContext);
                            checkCancellation(session.getSourceKey());
                            if (hasUsableRecoveryReply(recovered)) {
                                mergeUsage(result, recovered);
                                result = recovered;
                                currentReply = extractText(recovered.getAssistantMessage());
                            } else {
                                currentReply = MAX_STEPS_RECOVERY_FALLBACK;
                            }
                        }

                        if (StrUtil.isNotBlank(currentReply) || hasVisibleContent(result)) {
                            finalResult = result;
                            replyText = StrUtil.blankToDefault(currentReply, EMPTY_REPLY_FALLBACK);
                            eventSink.onAttemptCompleted(
                                    runRecord.getRunId(), attemptNo, "success", "");
                            runContext.event("attempt.success", "第 " + attemptNo + " 次尝试成功");
                            break;
                        }

                        lastError =
                                new IllegalStateException("LLM returned empty assistant content");
                        eventSink.onAttemptCompleted(
                                runRecord.getRunId(),
                                attemptNo,
                                "empty",
                                "LLM returned empty assistant content");
                        runContext.event("attempt.empty", "模型返回空内容");
                    } catch (AgentRunCancelledException e) {
                        throw e;
                    } catch (Exception e) {
                        if (isCancellationRequested(session.getSourceKey())) {
                            throw new AgentRunCancelledException();
                        }
                        lastError = e;
                        eventSink.onAttemptCompleted(
                                runRecord.getRunId(), attemptNo, "error", e.getMessage());
                        runContext.event(
                                "attempt.error", "第 " + attemptNo + " 次尝试失败：" + e.getMessage());
                        if (classifyRetryable(e) && attempt < maxAttempts) {
                            continue;
                        }
                    }
                }

                if (finalResult != null) {
                    break;
                }

                previousProvider = resolved.getProvider();
                if (candidateIndex + 1 < candidates.size()) {
                    AppConfig.LlmConfig next = candidates.get(candidateIndex + 1);
                    eventSink.onFallback(
                            runRecord.getRunId(),
                            previousProvider,
                            next.getProvider(),
                            lastError == null ? "empty response" : lastError.getMessage());
                    runContext.event(
                            "fallback",
                            "切换 fallback provider："
                                    + previousProvider
                                    + " -> "
                                    + next.getProvider());
                }
            }

            if (finalResult == null) {
                runRecord.setStatus("failed");
                runRecord.setFinishedAt(System.currentTimeMillis());
                runRecord.setError(
                        lastError == null ? "LLM execution failed" : lastError.getMessage());
                agentRunRepository.saveRun(runRecord);
                runContext.event("run.failed", runRecord.getError());
                if (lastError instanceof Exception) {
                    throw (Exception) lastError;
                }
                throw new IllegalStateException(runRecord.getError(), lastError);
            }

            checkCancellation(session.getSourceKey());
            session.setNdjson(finalResult.getNdjson());
            applyUsage(session, finalResult);
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);

            runRecord.setStatus("success");
            runRecord.setFinalReplyPreview(AgentRunContext.safe(replyText, 1000));
            runRecord.setInputTokens(finalResult.getInputTokens());
            runRecord.setOutputTokens(finalResult.getOutputTokens());
            runRecord.setTotalTokens(finalResult.getTotalTokens());
            runRecord.setProvider(finalResult.getProvider());
            runRecord.setModel(finalResult.getModel());
            runRecord.setFinishedAt(System.currentTimeMillis());
            agentRunRepository.saveRun(runRecord);
            runContext.event("run.success", "运行完成");

            AgentRunOutcome outcome = new AgentRunOutcome();
            outcome.setFinalReply(replyText);
            outcome.setResult(finalResult);
            outcome.setRunRecord(runRecord);
            outcome.setCompressionWarning(compressionWarning);
            outcome.setModel(finalResult.getModel());
            outcome.setProvider(finalResult.getProvider());
            outcome.setContextEstimateTokens(contextEstimateTokens);
            outcome.setContextWindowTokens(contextWindowTokens);
            outcome.setCwd(
                    StrUtil.blankToDefault(
                            agentScope.getWorkspaceDir(), System.getProperty("user.dir")));
            return outcome;
        } catch (AgentRunCancelledException e) {
            runRecord.setStatus("cancelled");
            runRecord.setFinishedAt(System.currentTimeMillis());
            runRecord.setError(e.getMessage());
            agentRunRepository.saveRun(runRecord);
            runContext.event("run.cancelled", e.getMessage());
            throw e;
        } finally {
            unregisterRun(session.getSourceKey(), runHandle);
            if (runHandle.cancelled.get()) {
                Thread.interrupted();
            }
        }
    }

    private CompressionOutcome compressBeforeAttempt(
            SessionRecord session,
            String systemPrompt,
            String userMessage,
            AppConfig.LlmConfig resolved,
            AgentRunContext runContext,
            ConversationEventSink eventSink,
            String runId)
            throws Exception {
        ContextBudgetDecision decision =
                contextBudgetService.decide(session, systemPrompt, userMessage, resolved);
        if (!decision.isShouldCompress()) {
            eventSink.onCompressionDecision(
                    runId,
                    false,
                    decision.getReason(),
                    decision.getEstimatedTokens(),
                    decision.getThresholdTokens());
            runContext.event(
                    "compression.skip",
                    decision.getReason(),
                    runContext.metadata("estimatedTokens", decision.getEstimatedTokens()));
            CompressionOutcome skipped = CompressionOutcome.skipped(session);
            skipped.setEstimatedTokens(decision.getEstimatedTokens());
            skipped.setThresholdTokens(decision.getThresholdTokens());
            return skipped;
        }

        SessionRecord before = cloneSessionState(session);
        CompressionOutcome outcome =
                contextCompressionService.compressNowWithOutcome(
                        session, systemPrompt, userMessage);
        SessionRecord compressed = outcome.getSession();
        boolean changed = !StrUtil.equals(before.getNdjson(), compressed.getNdjson());
        eventSink.onCompressionDecision(
                runId,
                changed,
                decision.getReason(),
                decision.getEstimatedTokens(),
                decision.getThresholdTokens());
        String eventType =
                outcome.isFailed()
                        ? "compression.failed"
                        : (changed ? "compression.done" : "compression.unchanged");
        runContext.event(
                eventType,
                outcome.isFailed() ? outcome.getErrorMessage() : decision.getReason(),
                runContext.metadata("estimatedTokens", decision.getEstimatedTokens()));
        if (changed) {
            sessionRepository.save(compressed);
        }
        outcome.setEstimatedTokens(decision.getEstimatedTokens());
        outcome.setThresholdTokens(decision.getThresholdTokens());
        return outcome;
    }

    private SessionRecord cloneSessionState(SessionRecord source) {
        SessionRecord clone = new SessionRecord();
        clone.setNdjson(source.getNdjson());
        return clone;
    }

    private LlmResult recover(
            SessionRecord session,
            String systemPrompt,
            String prompt,
            AppConfig.LlmConfig resolved,
            ConversationFeedbackSink feedbackSink,
            ConversationEventSink eventSink,
            AgentRunContext runContext) {
        try {
            return llmGateway.executeOnce(
                    session,
                    systemPrompt,
                    prompt,
                    Collections.emptyList(),
                    feedbackSink,
                    eventSink,
                    false,
                    resolved,
                    runContext);
        } catch (Exception e) {
            runContext.event("recovery.error", e.getMessage());
            log.warn("Agent recovery failed: sessionId={}", session.getSessionId(), e);
            return null;
        }
    }

    private List<AppConfig.LlmConfig> buildCandidateConfigs(
            SessionRecord session, AgentRuntimeScope agentScope) {
        List<AppConfig.LlmConfig> candidates = new java.util.ArrayList<AppConfig.LlmConfig>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        AppConfig.LlmConfig primary =
                toLlmConfig(
                        llmProviderService.resolveEffectiveProvider(
                                session, agentScope == null ? null : agentScope.getDefaultModel()));
        candidates.add(primary);
        seen.add(providerSignature(primary));
        for (LlmProviderService.ResolvedProvider fallback :
                llmProviderService.resolveFallbackProviders()) {
            AppConfig.LlmConfig candidate = toLlmConfig(fallback);
            if (seen.add(providerSignature(candidate))) {
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

    private String providerSignature(AppConfig.LlmConfig config) {
        return StrUtil.nullToEmpty(config.getProvider())
                + "|"
                + StrUtil.nullToEmpty(config.getDialect())
                + "|"
                + StrUtil.nullToEmpty(config.getApiUrl())
                + "|"
                + StrUtil.nullToEmpty(config.getModel())
                + "|"
                + (StrUtil.isBlank(config.getApiKey()) ? "no-key" : "has-key");
    }

    private String extractText(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return "";
        }
        if (StrUtil.isNotBlank(assistantMessage.getResultContent())) {
            return assistantMessage.getResultContent();
        }
        if (StrUtil.isNotBlank(assistantMessage.getContent())) {
            return assistantMessage.getContent();
        }
        return String.valueOf(assistantMessage);
    }

    private boolean hasVisibleContent(LlmResult result) {
        return result != null
                && (StrUtil.isNotBlank(extractText(result.getAssistantMessage()))
                        || StrUtil.isNotBlank(result.getRawResponse()));
    }

    private boolean hasRecentToolActivity(String previousNdjson, String currentNdjson) {
        try {
            List<ChatMessage> previous = MessageSupport.loadMessages(previousNdjson);
            List<ChatMessage> current = MessageSupport.loadMessages(currentNdjson);
            if (countTools(current) > countTools(previous)) {
                return true;
            }
            for (int i = current.size() - 1; i >= 0; i--) {
                ChatMessage message = current.get(i);
                if (message.getRole() == ChatRole.TOOL) {
                    return true;
                }
                if (message.getRole() == ChatRole.ASSISTANT
                        && StrUtil.isNotBlank(message.getContent())) {
                    return false;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private int countTools(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatRole.TOOL) {
                count++;
            }
        }
        return count;
    }

    private boolean hasUsableRecoveryReply(LlmResult recovered) {
        String text = recovered == null ? "" : extractText(recovered.getAssistantMessage());
        return StrUtil.isNotBlank(text) && !isMaxStepsReply(text);
    }

    private boolean isMaxStepsReply(String replyText) {
        if (StrUtil.isBlank(replyText)) {
            return false;
        }
        String normalized = replyText.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("agent error: maximum steps reached")
                || normalized.contains("maximum steps reached")
                || replyText.contains("已达到硬性步数上限");
    }

    private boolean classifyRetryable(Throwable error) {
        return LlmErrorClassifier.classify(error).isRetryable();
    }

    private void applyUsage(SessionRecord session, LlmResult result) {
        if (session == null || result == null) {
            return;
        }
        session.setLastInputTokens(result.getInputTokens());
        session.setLastOutputTokens(result.getOutputTokens());
        session.setLastReasoningTokens(result.getReasoningTokens());
        session.setLastCacheReadTokens(result.getCacheReadTokens());
        session.setLastCacheWriteTokens(result.getCacheWriteTokens());
        session.setLastTotalTokens(result.getTotalTokens());
        session.setCumulativeInputTokens(
                session.getCumulativeInputTokens() + Math.max(0L, result.getInputTokens()));
        session.setCumulativeOutputTokens(
                session.getCumulativeOutputTokens() + Math.max(0L, result.getOutputTokens()));
        session.setCumulativeReasoningTokens(
                session.getCumulativeReasoningTokens() + Math.max(0L, result.getReasoningTokens()));
        session.setCumulativeCacheReadTokens(
                session.getCumulativeCacheReadTokens() + Math.max(0L, result.getCacheReadTokens()));
        session.setCumulativeCacheWriteTokens(
                session.getCumulativeCacheWriteTokens()
                        + Math.max(0L, result.getCacheWriteTokens()));
        session.setCumulativeTotalTokens(
                session.getCumulativeTotalTokens() + Math.max(0L, result.getTotalTokens()));
        if (result.getTotalTokens() > 0
                || result.getInputTokens() > 0
                || result.getOutputTokens() > 0
                || result.getCacheReadTokens() > 0
                || result.getCacheWriteTokens() > 0) {
            session.setLastUsageAt(System.currentTimeMillis());
        }
        if (StrUtil.isNotBlank(result.getProvider())) {
            session.setLastResolvedProvider(result.getProvider());
        }
        if (StrUtil.isNotBlank(result.getModel())) {
            session.setLastResolvedModel(result.getModel());
        }
    }

    private void mergeUsage(LlmResult base, LlmResult extra) {
        if (base == null || extra == null) {
            return;
        }
        extra.setInputTokens(
                Math.max(0L, extra.getInputTokens()) + Math.max(0L, base.getInputTokens()));
        extra.setOutputTokens(
                Math.max(0L, extra.getOutputTokens()) + Math.max(0L, base.getOutputTokens()));
        extra.setReasoningTokens(
                Math.max(0L, extra.getReasoningTokens()) + Math.max(0L, base.getReasoningTokens()));
        extra.setCacheReadTokens(
                Math.max(0L, extra.getCacheReadTokens()) + Math.max(0L, base.getCacheReadTokens()));
        extra.setCacheWriteTokens(
                Math.max(0L, extra.getCacheWriteTokens())
                        + Math.max(0L, base.getCacheWriteTokens()));
        extra.setTotalTokens(
                Math.max(0L, extra.getTotalTokens()) + Math.max(0L, base.getTotalTokens()));
    }

    private RunHandle registerRun(
            String sourceKey, String runId, String sessionId, long startedAt) {
        RunHandle handle = new RunHandle(runId, sessionId, Thread.currentThread(), startedAt);
        runningRuns.put(normalizeSourceKey(sourceKey), handle);
        return handle;
    }

    private void unregisterRun(String sourceKey, RunHandle handle) {
        if (handle == null) {
            return;
        }
        runningRuns.remove(normalizeSourceKey(sourceKey), handle);
        lastRunFinishedAt = System.currentTimeMillis();
    }

    private void checkCancellation(String sourceKey) {
        if (isCancellationRequested(sourceKey)) {
            throw new AgentRunCancelledException();
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new AgentRunCancelledException();
        }
    }

    private boolean isCancellationRequested(String sourceKey) {
        RunHandle handle = runningRuns.get(normalizeSourceKey(sourceKey));
        return handle != null && handle.cancelled.get();
    }

    private String normalizeSourceKey(String sourceKey) {
        return StrUtil.blankToDefault(sourceKey, "__default__");
    }

    private static class RunHandle {
        private final String runId;
        private final String sessionId;
        private final Thread thread;
        private final long startedAt;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private RunHandle(String runId, String sessionId, Thread thread, long startedAt) {
            this.runId = runId;
            this.sessionId = sessionId;
            this.thread = thread;
            this.startedAt = startedAt;
        }
    }

    private void pruneOldRuns() {
        int days = appConfig.getTrace().getRetentionDays();
        if (days <= 0) {
            return;
        }
        try {
            agentRunRepository.pruneBefore(
                    System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L);
        } catch (Exception ignored) {
        }
    }
}
