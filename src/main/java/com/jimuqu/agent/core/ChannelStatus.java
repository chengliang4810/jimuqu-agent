package com.jimuqu.agent.core;

public class ChannelStatus {
    private PlatformType platform;
    private boolean enabled;
    private boolean connected;
    private String detail;

    public ChannelStatus() {
    }

    public ChannelStatus(PlatformType platform, boolean enabled, boolean connected, String detail) {
        this.platform = platform;
        this.enabled = enabled;
        this.connected = connected;
        this.detail = detail;
    }

    public PlatformType getPlatform() {
        return platform;
    }

    public void setPlatform(PlatformType platform) {
        this.platform = platform;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
