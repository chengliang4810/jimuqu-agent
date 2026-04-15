package com.jimuqu.agent.core;

public class GatewayReply {
    private String sessionId;
    private String branchName;
    private String content;
    private boolean commandHandled;
    private boolean error;

    public static GatewayReply ok(String content) {
        GatewayReply reply = new GatewayReply();
        reply.setContent(content);
        return reply;
    }

    public static GatewayReply error(String content) {
        GatewayReply reply = new GatewayReply();
        reply.setContent(content);
        reply.setError(true);
        return reply;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isCommandHandled() {
        return commandHandled;
    }

    public void setCommandHandled(boolean commandHandled) {
        this.commandHandled = commandHandled;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }
}
