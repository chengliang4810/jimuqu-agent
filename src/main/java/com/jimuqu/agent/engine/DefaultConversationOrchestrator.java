package com.jimuqu.agent.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.core.service.ContextService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.support.RuntimeSettingsService;
import com.jimuqu.agent.support.MessageAttachmentSupport;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.constants.CompressionConstants;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.Collections;
import java.util.List;

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

    private final SessionRepository sessionRepository;
    private final ContextService contextService;
    private final ContextCompressionService contextCompressionService;
    private final LlmGateway llmGateway;
    private final ToolRegistry toolRegistry;
    private final RuntimeSettingsService runtimeSettingsService;

    public GatewayReply handleIncoming(GatewayMessage message) throws Exception {
        SessionRecord session = sessionRepository.getBoundSession(message.sourceKey());
        if (session == null) {
            session = sessionRepository.bindNewSession(message.sourceKey());
        }
        return runOnSession(session, message);
    }

    public GatewayReply runScheduled(GatewayMessage syntheticMessage) throws Exception {
        SessionRecord session = sessionRepository.getBoundSession(syntheticMessage.sourceKey());
        if (session == null) {
            session = sessionRepository.bindNewSession(syntheticMessage.sourceKey());
        }
        return runOnSession(session, syntheticMessage);
    }

    private GatewayReply runOnSession(SessionRecord session, GatewayMessage message) throws Exception {
        String effectiveUserText = MessageAttachmentSupport.composeEffectiveUserText(message);
        message.setText(effectiveUserText);
        if (StrUtil.isBlank(session.getTitle()) && StrUtil.isNotBlank(effectiveUserText)) {
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
        LlmResult result = llmGateway.chat(session, systemPrompt, effectiveUserText, enabledTools);
        String replyText = extractText(result.getAssistantMessage());
        if (StrUtil.isBlank(replyText) && hasRecentToolActivity(previousNdjson, result.getNdjson())) {
            session.setNdjson(result.getNdjson());
            LlmResult recovered = tryRecoverEmptyReply(session, systemPrompt);
            if (recovered != null) {
                result = recovered;
                replyText = extractText(recovered.getAssistantMessage());
            }
        }

        session.setNdjson(result.getNdjson());
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        GatewayReply reply = GatewayReply.ok(StrUtil.blankToDefault(replyText, EMPTY_REPLY_FALLBACK));
        reply.setSessionId(session.getSessionId());
        reply.setBranchName(session.getBranchName());
        return reply;
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
}
