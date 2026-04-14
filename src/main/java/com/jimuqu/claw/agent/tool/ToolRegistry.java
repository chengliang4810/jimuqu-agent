package com.jimuqu.claw.agent.tool;

import org.noear.solon.ai.chat.tool.FunctionTool;

import java.util.Collection;

public interface ToolRegistry {
    Collection<FunctionTool> allTools();

    FunctionTool get(String name);
}
