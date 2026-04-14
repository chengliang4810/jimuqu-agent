package com.jimuqu.claw.agent.workspace;

import com.jimuqu.claw.agent.runtime.model.SessionContext;

public interface WorkspacePromptService {
    String buildSystemPrompt(SessionContext sessionContext);
}
