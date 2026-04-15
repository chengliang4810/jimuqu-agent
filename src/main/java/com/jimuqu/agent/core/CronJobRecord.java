package com.jimuqu.agent.core;

public class CronJobRecord {
    private String jobId;
    private String name;
    private String cronExpr;
    private String prompt;
    private String sourceKey;
    private String deliverPlatform;
    private String deliverChatId;
    private String status;
    private long nextRunAt;
    private long lastRunAt;
    private long createdAt;
    private long updatedAt;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public void setSourceKey(String sourceKey) {
        this.sourceKey = sourceKey;
    }

    public String getDeliverPlatform() {
        return deliverPlatform;
    }

    public void setDeliverPlatform(String deliverPlatform) {
        this.deliverPlatform = deliverPlatform;
    }

    public String getDeliverChatId() {
        return deliverChatId;
    }

    public void setDeliverChatId(String deliverChatId) {
        this.deliverChatId = deliverChatId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(long nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public long getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(long lastRunAt) {
        this.lastRunAt = lastRunAt;
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
