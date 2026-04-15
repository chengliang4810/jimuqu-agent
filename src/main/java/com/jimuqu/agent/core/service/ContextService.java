package com.jimuqu.agent.core.service;

/**
 * 上下文拼装服务接口。
 */
public interface ContextService {
    /**
     * 构建来源键对应的系统提示词。
     */
    String buildSystemPrompt(String sourceKey);
}
