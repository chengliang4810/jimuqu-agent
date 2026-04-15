package com.jimuqu.agent.core;

public class PairingRateLimitRecord {
    private PlatformType platform;
    private String userId;
    private long requestedAt;
    private int failedAttempts;
    private long lockoutUntil;

    public PlatformType getPlatform() {
        return platform;
    }

    public void setPlatform(PlatformType platform) {
        this.platform = platform;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(long requestedAt) {
        this.requestedAt = requestedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public long getLockoutUntil() {
        return lockoutUntil;
    }

    public void setLockoutUntil(long lockoutUntil) {
        this.lockoutUntil = lockoutUntil;
    }
}
