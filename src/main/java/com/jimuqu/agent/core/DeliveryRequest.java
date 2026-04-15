package com.jimuqu.agent.core;

public class DeliveryRequest {
    private PlatformType platform;
    private String chatId;
    private String userId;
    private String threadId;
    private String text;

    public DeliveryRequest() {
    }

    public DeliveryRequest(PlatformType platform, String chatId, String userId, String threadId, String text) {
        this.platform = platform;
        this.chatId = chatId;
        this.userId = userId;
        this.threadId = threadId;
        this.text = text;
    }

    public PlatformType getPlatform() {
        return platform;
    }

    public void setPlatform(PlatformType platform) {
        this.platform = platform;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
