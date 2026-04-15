package com.jimuqu.agent.core;

public interface ConversationOrchestrator {
    GatewayReply handleIncoming(GatewayMessage message) throws Exception;

    GatewayReply runScheduled(GatewayMessage syntheticMessage) throws Exception;
}
