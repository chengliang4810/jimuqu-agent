package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;

/**
 * Agent 主循环调度接口。
 */
public interface ConversationOrchestrator {
    /**
     * 处理普通入站消息。
     */
    GatewayReply handleIncoming(GatewayMessage message) throws Exception;

    /**
     * 处理定时任务触发的消息。
     */
    GatewayReply runScheduled(GatewayMessage syntheticMessage) throws Exception;
}
