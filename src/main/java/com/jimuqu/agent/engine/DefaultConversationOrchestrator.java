package com.jimuqu.agent.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.core.service.ContextService;
import com.jimuqu.agent.core.service.ConversationEventSink;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.gateway.feedback.ConversationFeedbackSink;
import com.jimuqu.agent.gateway.feedback.GatewayConversationFeedbackSink;
import com.jimuqu.agent.support.DisplaySettingsService;
import com.jimuqu.agent.support.RuntimeSettingsService;
import com.jimuqu.agent.support.MessageAttachmentSupport;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.SourceKeySupport;
import com.jimuqu.agent.support.constants.CompressionConstants;
import com.jimuqu.agent.tool.runtime.DangerousCommandApprovalService;
import com.jimuqu.agent.tool.runtime.MessageDeliveryTracker;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * DefaultConversationOrchestrator 实现。
 */
@RequiredArgsConstructor
public class DefaultConversationOrchestrator implements ConversationOrchestrator {
    /**
     * 当模型只完成工具调用却未生成最终文字答复时，补发的恢复提示。
     */
    private static final String EMPTY_REPLY_RECOVERY_PROMPT = "你刚刚已经完成了工具调用，但没有输出最终答复。请基于当前会话中的最新工具结果，直接用中文给出简洁最终答复，不要再次调用工具。";

    /**
     * 当恢复仍失败时返回给用户的兜底文案。
     */
    private static final String EMPTY_REPLY_FALLBACK = "本轮已完成工具调用，但模型没有返回可读结论。请使用 /retry 重试，或继续给出下一步指令。";

    /**
     * 当 ReAct 步数耗尽时，要求模型基于现有轨迹做一次无工具收敛总结。
     */
    private static final String MAX_STEPS_RECOVERY_PROMPT = "你刚刚因为最大推理步数限制而停止。不要再次调用工具。请基于当前会话中已经完成的分析、工具结果、文件修改和观察，直接输出中文收敛答复：优先给出已经完成的结果；若任务仍未彻底完成，明确说明还差什么、最推荐的下一步是什么。";

    /**
     * 当步数耗尽后的收敛恢复仍失败时，返回给用户的兜底文案。
     */
    private static final String MAX_STEPS_RECOVERY_FALLBACK = "本轮执行已达到最大步骤限制，已保留当前进展。请继续给出更聚焦的下一步，或使用 /retry 继续。";

    private final SessionRepository sessionRepository;
    private final ContextService contextService;
    private final ContextCompressionService contextCompressionService;
    private final LlmGateway llmGateway;
    private final ToolRegistry toolRegistry;
    private final DeliveryService deliveryService;
    private final DisplaySettingsService displaySettingsService;
    private final RuntimeSettingsService runtimeSettingsService;
    private final DangerousCommandApprovalService dangerousCommandApprovalService;
    private final ConcurrentMap<String, Object> sourceLocks = new ConcurrentHashMap<String, Object>();

    public GatewayReply handleIncoming(GatewayMessage message) throws Exception {
        return handleIncoming(message, ConversationEventSink.noop());
    }

    public GatewayReply handleIncoming(GatewayMessage message, ConversationEventSink eventSink) throws Exception {
        String sourceKey = message.sourceKey();
        synchronized (lockFor(sourceKey)) {
            SessionRecord session = sessionRepository.getBoundSession(sourceKey);
            if (session == null) {
                session = sessionRepository.bindNewSession(sourceKey);
            }
            return runOnSession(session, message, eventSink);
        }
    }

    public GatewayReply runScheduled(GatewayMessage syntheticMessage) throws Exception {
        return runScheduled(syntheticMessage, ConversationEventSink.noop());
    }

    public GatewayReply runScheduled(GatewayMessage syntheticMessage, ConversationEventSink eventSink) throws Exception {
        String sourceKey = syntheticMessage.sourceKey();
        synchronized (lockFor(sourceKey)) {
            SessionRecord session = sessionRepository.getBoundSession(sourceKey);
            if (session == null) {
                session = sessionRepository.bindNewSession(sourceKey);
            }
            return runOnSession(session, syntheticMessage, eventSink);
        }
    }

    public GatewayReply resumePending(String sourceKey) throws Exception {
        return resumePending(sourceKey, ConversationEventSink.noop());
    }

    public GatewayReply resumePending(String sourceKey, ConversationEventSink eventSink) throws Exception {
        synchronized (lockFor(sourceKey)) {
        SessionRecord session = sessionRepository.getBoundSession(sourceKey);
        if (session == null) {
            return GatewayReply.error("当前来源键没有可恢复的会话。");
        }

        List<String> enabledToolNames = toolRegistry.resolveEnabledToolNames(sourceKey);
        List<Object> enabledTools = toolRegistry.resolveEnabledTools(sourceKey);
        String systemPrompt = contextService.buildSystemPrompt(sourceKey)
                + "\n\n"
                + runtimeSettingsService.buildAgentRuntimePrompt(sourceKey, session, enabledToolNames);
        session.setSystemPromptSnapshot(systemPrompt);

        String previousNdjson = session.getNdjson();
        GatewayMessage feedbackTarget = messageFromSourceKey(sourceKey);
        ConversationFeedbackSink feedbackSink = feedbackSinkFor(feedbackTarget);
        LlmResult result = llmGateway.resume(session, systemPrompt, enabledTools, feedbackSink, eventSink);
        String replyText = extractText(result.getAssistantMessage());
        if (StrUtil.isBlank(replyText) && hasRecentToolActivity(previousNdjson, result.getNdjson())) {
            session.setNdjson(result.getNdjson());
            LlmResult recovered = tryRecoverEmptyReply(session, systemPrompt);
            if (recovered != null) {
                mergeUsage(result, recovered);
                result = recovered;
                replyText = extractText(recovered.getAssistantMessage());
            }
        }

        if (isMaxStepsReply(replyText)) {
            session.setNdjson(result.getNdjson());
            LlmResult recovered = tryRecoverMaxStepsReply(session, systemPrompt);
            if (hasUsableRecoveryReply(recovered)) {
                mergeUsage(result, recovered);
                result = recovered;
                replyText = extractText(recovered.getAssistantMessage());
            } else {
                replyText = MAX_STEPS_RECOVERY_FALLBACK;
            }
        }

        session.setNdjson(result.getNdjson());
        applyUsage(session, result);
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        String finalReply = StrUtil.blankToDefault(replyText, EMPTY_REPLY_FALLBACK);
        feedbackSink.onFinalReply(finalReply);
        eventSink.onRunCompleted(session.getSessionId(), finalReply, result);
        GatewayReply reply = GatewayReply.ok(finalReply);
        reply.setSessionId(session.getSessionId());
        reply.setBranchName(session.getBranchName());
        return reply;
        }
    }

    private Object lockFor(String sourceKey) {
        String key = StrUtil.blankToDefault(sourceKey, "__default__");
        Object existing = sourceLocks.get(key);
        if (existing != null) {
            return existing;
        }
        Object created = new Object();
        Object previous = sourceLocks.putIfAbsent(key, created);
        return previous == null ? created : previous;
    }

    private GatewayReply runOnSession(SessionRecord session, GatewayMessage message, ConversationEventSink eventSink) throws Exception {
        String effectiveUserText = MessageAttachmentSupport.composeEffectiveUserText(message);
        message.setText(effectiveUserText);
        if (!message.isHeartbeat() && StrUtil.isBlank(session.getTitle()) && StrUtil.isNotBlank(effectiveUserText)) {
            session.setTitle(extractTitle(effectiveUserText));
        }
        List<String> enabledToolNames = toolRegistry.resolveEnabledToolNames(message.sourceKey());
        List<Object> enabledTools = toolRegistry.resolveEnabledTools(message.sourceKey());
        String systemPrompt = contextService.buildSystemPrompt(message.sourceKey())
                + "\n\n"
                + runtimeSettingsService.buildAgentRuntimePrompt(message.sourceKey(), session, enabledToolNames);
        session.setSystemPromptSnapshot(systemPrompt);

        session = contextCompressionService.compressIfNeeded(session, systemPrompt, effectiveUserText);
        String previousNdjson = session.getNdjson();
        ConversationFeedbackSink feedbackSink = feedbackSinkFor(message);
        LlmResult result = llmGateway.chat(session, systemPrompt, effectiveUserText, enabledTools, feedbackSink, eventSink);
        String replyText = extractText(result.getAssistantMessage());
        if (StrUtil.isBlank(replyText) && hasRecentToolActivity(previousNdjson, result.getNdjson())) {
            session.setNdjson(result.getNdjson());
            LlmResult recovered = tryRecoverEmptyReply(session, systemPrompt);
            if (recovered != null) {
                mergeUsage(result, recovered);
                result = recovered;
                replyText = extractText(recovered.getAssistantMessage());
            }
        }

        if (isMaxStepsReply(replyText)) {
            session.setNdjson(result.getNdjson());
            LlmResult recovered = tryRecoverMaxStepsReply(session, systemPrompt);
            if (hasUsableRecoveryReply(recovered)) {
                mergeUsage(result, recovered);
                result = recovered;
                replyText = extractText(recovered.getAssistantMessage());
            } else {
                replyText = MAX_STEPS_RECOVERY_FALLBACK;
            }
        }

        session.setNdjson(result.getNdjson());
        applyUsage(session, result);
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        String finalReply = StrUtil.blankToDefault(replyText, EMPTY_REPLY_FALLBACK);
        if (MessageDeliveryTracker.consumeDuplicateFinalReply(message.sourceKey(), finalReply)) {
            finalReply = "";
        }
        feedbackSink.onFinalReply(finalReply);
        eventSink.onRunCompleted(session.getSessionId(), finalReply, result);
        GatewayReply reply = GatewayReply.ok(finalReply);
        reply.setSessionId(session.getSessionId());
        reply.setBranchName(session.getBranchName());
        applyApprovalCardIfNeeded(reply, message.getPlatform(), session);
        return reply;
    }

    private ConversationFeedbackSink feedbackSinkFor(GatewayMessage message) {
        if (message == null || message.isHeartbeat() || message.getPlatform() == null || message.getPlatform() == PlatformType.MEMORY) {
            return ConversationFeedbackSink.noop();
        }
        return new GatewayConversationFeedbackSink(message, deliveryService, displaySettingsService);
    }

    private GatewayMessage messageFromSourceKey(String sourceKey) {
        String[] parts = SourceKeySupport.split(sourceKey);
        PlatformType platform = PlatformType.fromName(parts[0]);
        GatewayMessage message = new GatewayMessage(platform, parts[1], parts[2], "");
        message.setSourceKeyOverride(sourceKey);
        return message;
    }

    private void applyApprovalCardIfNeeded(GatewayReply reply, PlatformType platform, SessionRecord session) {
        if (reply == null || platform == null || session == null || dangerousCommandApprovalService == null) {
            return;
        }

        DangerousCommandApprovalService.PendingApproval pending = dangerousCommandApprovalService.getPendingApproval(session);
        if (pending == null) {
            return;
        }

        reply.getChannelExtras().putAll(dangerousCommandApprovalService.buildDeliveryExtras(platform, pending));
    }

    private String extractText(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            return "";
        }

        if (assistantMessage.getResultContent() != null && assistantMessage.getResultContent().trim().length() > 0) {
            return assistantMessage.getResultContent();
        }

        if (assistantMessage.getContent() != null && assistantMessage.getContent().trim().length() > 0) {
            return assistantMessage.getContent();
        }

        return assistantMessage.toString();
    }

    /**
     * 从第一条用户文本生成会话标题。
     */
    private String extractTitle(String text) {
        String normalized = text.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= CompressionConstants.MAX_TITLE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, CompressionConstants.MAX_TITLE_LENGTH) + "...";
    }

    /**
     * 判断本轮是否发生了有效工具调用，便于在空回复时做一次恢复。
     */
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
                if (message.getRole() == ChatRole.ASSISTANT && StrUtil.isNotBlank(message.getContent())) {
                    return false;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    /**
     * 针对“工具执行成功但文字为空”的情况做一次无工具恢复调用。
     */
    private LlmResult tryRecoverEmptyReply(SessionRecord session, String systemPrompt) {
        try {
            return llmGateway.chat(session, systemPrompt, EMPTY_REPLY_RECOVERY_PROMPT, Collections.emptyList());
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 针对“达到最大步数上限”场景，再做一次无工具收敛总结。
     */
    private LlmResult tryRecoverMaxStepsReply(SessionRecord session, String systemPrompt) {
        try {
            return llmGateway.chat(session, systemPrompt, MAX_STEPS_RECOVERY_PROMPT, Collections.emptyList());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasUsableRecoveryReply(LlmResult recovered) {
        if (recovered == null) {
            return false;
        }
        String text = extractText(recovered.getAssistantMessage());
        return StrUtil.isNotBlank(text) && !isMaxStepsReply(text);
    }

    private boolean isMaxStepsReply(String replyText) {
        if (StrUtil.isBlank(replyText)) {
            return false;
        }

        String normalized = replyText.trim().toLowerCase();
        return normalized.startsWith("agent error: maximum steps reached")
                || normalized.contains("maximum steps reached")
                || replyText.contains("已达到硬性步数上限");
    }

    /**
     * 统计工具消息数量。
     */
    private int countTools(List<ChatMessage> messages) {
        int count = 0;
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatRole.TOOL) {
                count++;
            }
        }
        return count;
    }

    /**
     * 将本轮 usage 汇总写入会话记录。
     */
    private void applyUsage(SessionRecord session, LlmResult result) {
        if (session == null || result == null) {
            return;
        }

        session.setLastInputTokens(result.getInputTokens());
        session.setLastOutputTokens(result.getOutputTokens());
        session.setLastReasoningTokens(result.getReasoningTokens());
        session.setLastCacheReadTokens(result.getCacheReadTokens());
        session.setLastTotalTokens(result.getTotalTokens());

        session.setCumulativeInputTokens(session.getCumulativeInputTokens() + Math.max(0L, result.getInputTokens()));
        session.setCumulativeOutputTokens(session.getCumulativeOutputTokens() + Math.max(0L, result.getOutputTokens()));
        session.setCumulativeReasoningTokens(session.getCumulativeReasoningTokens() + Math.max(0L, result.getReasoningTokens()));
        session.setCumulativeCacheReadTokens(session.getCumulativeCacheReadTokens() + Math.max(0L, result.getCacheReadTokens()));
        session.setCumulativeTotalTokens(session.getCumulativeTotalTokens() + Math.max(0L, result.getTotalTokens()));

        if (result.getTotalTokens() > 0 || result.getInputTokens() > 0 || result.getOutputTokens() > 0) {
            session.setLastUsageAt(System.currentTimeMillis());
        }

        if (StrUtil.isNotBlank(result.getProvider())) {
            session.setLastResolvedProvider(result.getProvider());
        }
        if (StrUtil.isNotBlank(result.getModel())) {
            session.setLastResolvedModel(result.getModel());
        }
    }

    /**
     * 将恢复调用前已消耗的 usage 合并进最终结果。
     */
    private void mergeUsage(LlmResult base, LlmResult extra) {
        if (base == null || extra == null) {
            return;
        }
        extra.setInputTokens(Math.max(0L, extra.getInputTokens()) + Math.max(0L, base.getInputTokens()));
        extra.setOutputTokens(Math.max(0L, extra.getOutputTokens()) + Math.max(0L, base.getOutputTokens()));
        extra.setReasoningTokens(Math.max(0L, extra.getReasoningTokens()) + Math.max(0L, base.getReasoningTokens()));
        extra.setCacheReadTokens(Math.max(0L, extra.getCacheReadTokens()) + Math.max(0L, base.getCacheReadTokens()));
        extra.setTotalTokens(Math.max(0L, extra.getTotalTokens()) + Math.max(0L, base.getTotalTokens()));
    }
}
