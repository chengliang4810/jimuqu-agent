package com.jimuqu.agent.tool;

import com.jimuqu.agent.core.ConversationOrchestrator;
import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.core.PlatformType;
import org.noear.solon.ai.annotation.ToolMapping;

public class DelegateTools {
    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<Integer>();

    private final ConversationOrchestrator conversationOrchestrator;
    private final String sourceKey;

    public DelegateTools(ConversationOrchestrator conversationOrchestrator, String sourceKey) {
        this.conversationOrchestrator = conversationOrchestrator;
        this.sourceKey = sourceKey;
    }

    @ToolMapping(name = "delegate_task", description = "Delegate a subtask to a nested agent call. Limited to one nested level.")
    public String delegateTask(String prompt) throws Exception {
        if (conversationOrchestrator == null) {
            return "Delegate tool is not ready";
        }

        Integer depth = DEPTH.get();
        if (depth == null) {
            depth = 0;
        }

        if (depth.intValue() >= 1) {
            return "Delegate depth limit reached";
        }

        String[] parts = sourceKey.split(":", 3);
        GatewayMessage message = new GatewayMessage(PlatformType.fromName(parts[0]), parts[1], parts.length > 2 ? parts[2] : "", prompt);
        DEPTH.set(Integer.valueOf(depth.intValue() + 1));
        try {
            GatewayReply reply = conversationOrchestrator.runScheduled(message);
            return reply.getContent();
        } finally {
            DEPTH.set(depth);
        }
    }
}
