package com.jimuqu.agent.support;

import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.ai.chat.session.InMemoryChatSession;

import java.util.List;

public class FakeLlmGateway implements LlmGateway {
    public String lastSystemPrompt;

    public LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception {
        lastSystemPrompt = systemPrompt;
        InMemoryChatSession chatSession = new InMemoryChatSession(session.getSessionId());
        if (session.getNdjson() != null && session.getNdjson().trim().length() > 0) {
            chatSession.loadNdjson(session.getNdjson());
        }
        chatSession.addMessage(ChatMessage.ofUser(userMessage));
        chatSession.addMessage(ChatMessage.ofAssistant("echo:" + userMessage));

        LlmResult result = new LlmResult();
        result.setAssistantMessage(ChatMessage.ofAssistant("echo:" + userMessage));
        result.setNdjson(chatSession.toNdjson());
        result.setRawResponse("fake");
        result.setStreamed(false);
        return result;
    }
}

