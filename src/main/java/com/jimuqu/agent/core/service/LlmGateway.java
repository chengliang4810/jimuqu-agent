package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.model.LlmResult;
import com.jimuqu.agent.core.model.SessionRecord;

import java.util.List;

/**
 * 大模型调用网关接口。
 */
public interface LlmGateway {
    /**
     * 发起一次聊天调用。
     *
     * @param session      当前会话
     * @param systemPrompt 系统提示词
     * @param userMessage  用户输入
     * @param toolObjects  当前可用工具
     * @return 模型调用结果
     */
    LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception;
}
