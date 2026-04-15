package com.jimuqu.agent.engine;

import com.jimuqu.agent.core.service.ContextService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.ToolRegistry;
import org.noear.solon.ai.chat.message.AssistantMessage;

/**
 * DefaultConversationOrchestrator 实现。
 */
public class DefaultConversationOrchestrator implements ConversationOrchestrator {
    private final SessionRepository sessionRepository;
    private final ContextService contextService;
    private final LlmGateway llmGateway;
    private final ToolRegistry toolRegistry;

    public DefaultConversationOrchestrator(SessionRepository sessionRepository,
                                           ContextService contextService,
                                           LlmGateway llmGateway,
                                           ToolRegistry toolRegistry) {
        this.sessionRepository = sessionRepository;
        this.contextService = contextService;
        this.llmGateway = llmGateway;
        this.toolRegistry = toolRegistry;
    }

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
        String systemPrompt = contextService.buildSystemPrompt(message.sourceKey());
        LlmResult result = llmGateway.chat(session, systemPrompt, message.getText(), toolRegistry.resolveEnabledTools(message.sourceKey()));
        session.setNdjson(result.getNdjson());
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        GatewayReply reply = GatewayReply.ok(extractText(result.getAssistantMessage()));
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
}
