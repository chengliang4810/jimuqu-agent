package com.jimuqu.agent.core;

import java.util.List;

public interface LlmGateway {
    LlmResult chat(SessionRecord session, String systemPrompt, String userMessage, List<Object> toolObjects) throws Exception;
}
