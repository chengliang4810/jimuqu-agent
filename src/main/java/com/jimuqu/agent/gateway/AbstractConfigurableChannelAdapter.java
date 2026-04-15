package com.jimuqu.agent.gateway;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.ChannelAdapter;
import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.InboundMessageHandler;
import com.jimuqu.agent.core.PlatformType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConfigurableChannelAdapter implements ChannelAdapter {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final PlatformType platformType;
    private final boolean enabled;
    private boolean connected;
    private String detail;
    private InboundMessageHandler inboundMessageHandler;

    protected AbstractConfigurableChannelAdapter(PlatformType platformType, AppConfig.ChannelConfig config) {
        this.platformType = platformType;
        this.enabled = config != null && config.isEnabled();
        this.detail = enabled ? "configured" : "disabled";
    }

    public PlatformType platform() {
        return platformType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean connect() {
        if (!enabled) {
            detail = "disabled";
            return false;
        }

        connected = true;
        detail = "adapter scaffold ready";
        return true;
    }

    public void disconnect() {
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public String detail() {
        return detail;
    }

    public void send(DeliveryRequest request) throws Exception {
        log.info("[{}:{}] {}", request.getPlatform(), request.getChatId(), request.getText());
    }

    public void setInboundMessageHandler(InboundMessageHandler inboundMessageHandler) {
        this.inboundMessageHandler = inboundMessageHandler;
    }

    protected InboundMessageHandler inboundMessageHandler() {
        return inboundMessageHandler;
    }

    protected void setConnected(boolean connected) {
        this.connected = connected;
    }

    protected void setDetail(String detail) {
        this.detail = detail;
    }
}
