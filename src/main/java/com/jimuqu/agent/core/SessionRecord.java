package com.jimuqu.agent.core;

public class SessionRecord {
    private String sessionId;
    private String sourceKey;
    private String branchName;
    private String parentSessionId;
    private String modelOverride;
    private String ndjson;
    private long createdAt;
    private long updatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public void setParentSessionId(String parentSessionId) {
        this.parentSessionId = parentSessionId;
    }

    public String getModelOverride() {
        return modelOverride;
    }

    public void setModelOverride(String modelOverride) {
        this.modelOverride = modelOverride;
    }

    public String getNdjson() {
        return ndjson;
    }

    public void setNdjson(String ndjson) {
        this.ndjson = ndjson;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
