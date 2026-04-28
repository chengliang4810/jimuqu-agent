package com.jimuqu.agent.core.service;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.ContextBudgetDecision;
import com.jimuqu.agent.core.model.SessionRecord;

/**
 * 模型调用前的上下文预算服务。
 */
public interface ContextBudgetService {
    ContextBudgetDecision decide(SessionRecord session, String systemPrompt, String userMessage, AppConfig.LlmConfig resolved);
}
