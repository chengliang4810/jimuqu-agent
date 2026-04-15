package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.DeliveryRequest;

/**
 * 渠道适配器统一接口。
 */
public interface ChannelAdapter {
    /**
     * 返回适配器所属平台。
     */
    PlatformType platform();

    /**
     * 当前适配器是否启用。
     */
    boolean isEnabled();

    /**
     * 建立连接。
     */
    boolean connect();

    /**
     * 关闭连接。
     */
    void disconnect();

    /**
     * 当前是否已连接。
     */
    boolean isConnected();

    /**
     * 返回适配器运行详情。
     */
    String detail();

    /**
     * 发送一条消息。
     */
    void send(DeliveryRequest request) throws Exception;

    /**
     * 注册入站消息处理器。
     */
    default void setInboundMessageHandler(InboundMessageHandler inboundMessageHandler) {
        // 留给具体渠道按需实现。
    }
}
