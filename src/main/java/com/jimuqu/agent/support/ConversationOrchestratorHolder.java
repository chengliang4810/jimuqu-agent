package com.jimuqu.agent.support;

import com.jimuqu.agent.core.ConversationOrchestrator;

public class ConversationOrchestratorHolder {
    private volatile ConversationOrchestrator conversationOrchestrator;

    public ConversationOrchestrator get() {
        return conversationOrchestrator;
    }

    public void set(ConversationOrchestrator conversationOrchestrator) {
        this.conversationOrchestrator = conversationOrchestrator;
    }
}
