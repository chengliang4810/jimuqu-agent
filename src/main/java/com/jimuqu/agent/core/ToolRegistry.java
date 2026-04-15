package com.jimuqu.agent.core;

import java.util.List;

public interface ToolRegistry {
    List<String> listToolNames();

    List<Object> resolveEnabledTools(String sourceKey);

    void enableTools(String sourceKey, List<String> toolNames);

    void disableTools(String sourceKey, List<String> toolNames);
}
