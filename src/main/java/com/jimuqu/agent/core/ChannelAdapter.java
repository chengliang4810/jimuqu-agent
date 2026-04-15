package com.jimuqu.agent.core;

public interface ChannelAdapter {
    PlatformType platform();

    boolean isEnabled();

    boolean connect();

    void disconnect();

    boolean isConnected();

    String detail();

    void send(DeliveryRequest request) throws Exception;

    default void setInboundMessageHandler(InboundMessageHandler inboundMessageHandler) {
        // optional
    }
}
