package com.jimuqu.agent.core;

public class GatewayMessage {
    private PlatformType platform;
    private String chatId;
    private String userId;
    private String chatType;
    private String chatName;
    private String userName;
    private String text;
    private String threadId;
    private long timestamp;

    public GatewayMessage() {
    }

    public GatewayMessage(PlatformType platform, String chatId, String userId, String text) {
        this.platform = platform;
        this.chatId = chatId;
        this.userId = userId;
        this.chatType = "dm";
        this.chatName = chatId;
        this.userName = userId;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public String sourceKey() {
        return String.valueOf(platform) + ":" + nullToEmpty(chatId) + ":" + nullToEmpty(userId);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public String getChatName() {
        return chatName;
    }

    public void setChatName(String chatName) {
        this.chatName = chatName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
