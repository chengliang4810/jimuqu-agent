package com.jimuqu.agent.gateway.platform.base;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.service.ChannelAdapter;
import com.jimuqu.agent.core.service.InboundMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可配置渠道适配器基类，负责处理启用状态、连接状态和基础日志。
 */
public abstract class AbstractConfigurableChannelAdapter implements ChannelAdapter {
    /**
     * 渠道日志器。
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 当前适配器对应的平台。
     */
    private final PlatformType platformType;

    /**
     * 是否启用。
     */
    private final boolean enabled;

    /**
     * 当前连接状态。
     */
    private boolean connected;

    /**
     * 当前详情描述。
     */
    private String detail;

    /**
     * 入站消息处理器。
     */
    private InboundMessageHandler inboundMessageHandler;

    /**
     * 构造基础适配器。
     */
    protected AbstractConfigurableChannelAdapter(PlatformType platformType, AppConfig.ChannelConfig config) {
        this.platformType = platformType;
        this.enabled = config != null && config.isEnabled();
        this.detail = enabled ? "configured" : "disabled";
    }

    /**
     * 返回所属平台。
     */
    @Override
    public PlatformType platform() {
        return platformType;
    }

    /**
     * 返回是否启用。
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 建立基础连接状态。
     */
    @Override
    public boolean connect() {
        if (!enabled) {
            detail = "disabled";
            return false;
        }

        connected = true;
        detail = "adapter scaffold ready";
        return true;
    }

    /**
     * 断开连接。
     */
    @Override
    public void disconnect() {
        connected = false;
    }

    /**
     * 返回当前是否已连接。
     */
    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * 返回当前详情。
     */
    @Override
    public String detail() {
        return detail;
    }

    /**
     * 默认发送实现仅打日志，具体渠道可覆盖。
     */
    @Override
    public void send(DeliveryRequest request) throws Exception {
        log.info("[{}:{}] {}", request.getPlatform(), request.getChatId(), request.getText());
    }

    /**
     * 注册入站消息处理器。
     */
    @Override
    public void setInboundMessageHandler(InboundMessageHandler inboundMessageHandler) {
        this.inboundMessageHandler = inboundMessageHandler;
    }

    /**
     * 供子类读取当前入站处理器。
     */
    protected InboundMessageHandler inboundMessageHandler() {
        return inboundMessageHandler;
    }

    /**
     * 更新连接状态。
     */
    protected void setConnected(boolean connected) {
        this.connected = connected;
    }

    /**
     * 更新详情。
     */
    protected void setDetail(String detail) {
        this.detail = detail;
    }
}
