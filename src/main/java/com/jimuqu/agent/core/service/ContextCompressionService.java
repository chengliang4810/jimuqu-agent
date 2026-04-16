package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.model.SessionRecord;

/**
 * 上下文压缩服务接口。
 */
public interface ContextCompressionService {
    /**
     * 在模型调用前按需压缩会话上下文。
     */
    SessionRecord compressIfNeeded(SessionRecord session, String systemPrompt, String userMessage) throws Exception;

    /**
     * 强制压缩当前会话。
     */
    SessionRecord compressNow(SessionRecord session, String systemPrompt) throws Exception;
}
