package com.jimuqu.agent.core;

public interface InboundMessageHandler {
    void handle(GatewayMessage message) throws Exception;
}
