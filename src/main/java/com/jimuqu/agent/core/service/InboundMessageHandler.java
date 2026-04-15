package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.model.GatewayMessage;

/**
 * 渠道入站消息回调接口。
 */
public interface InboundMessageHandler {
    /**
     * 处理单条渠道入站消息。
     */
    void handle(GatewayMessage message) throws Exception;
}
