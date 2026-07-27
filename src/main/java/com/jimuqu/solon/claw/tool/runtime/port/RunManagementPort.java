package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层查询和控制 Agent 运行的同步端口。 */
public interface RunManagementPort {
    /** 查询会话运行。 */
    Map<String, Object> sessionRuns(String sessionId, int limit) throws Exception;

    /** 查询运行摘要。 */
    Map<String, Object> run(String runId) throws Exception;

    /** 查询运行详情。 */
    Map<String, Object> detail(String runId) throws Exception;

    /** 查询可恢复运行。 */
    Map<String, Object> recoverable(int limit) throws Exception;

    /** 查询活跃子 Agent。 */
    Map<String, Object> activeSubagents();

    /** 控制子 Agent。 */
    Map<String, Object> controlSubagent(String subagentId, String command);

    /** 控制运行。 */
    Map<String, Object> control(String runId, String command, Map<String, Object> payload)
            throws Exception;

    /** 查询运行事件。 */
    Map<String, Object> events(String runId) throws Exception;

    /** 查询工具调用。 */
    Map<String, Object> toolCalls(String runId) throws Exception;

    /** 查询子 Agent 运行。 */
    Map<String, Object> subagents(String runId) throws Exception;

    /** 查询恢复记录。 */
    Map<String, Object> recoveries(String runId) throws Exception;

    /** 查询控制命令。 */
    Map<String, Object> commands(String runId) throws Exception;
}
