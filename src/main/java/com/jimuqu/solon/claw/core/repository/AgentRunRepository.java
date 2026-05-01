package com.jimuqu.solon.claw.core.repository;

import com.jimuqu.solon.claw.core.model.AgentRunEventRecord;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.RunRecoveryRecord;
import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import java.util.List;

/** Agent 运行轨迹仓储。 */
public interface AgentRunRepository {
    void saveRun(AgentRunRecord record) throws Exception;

    AgentRunRecord findRun(String runId) throws Exception;

    List<AgentRunRecord> listBySession(String sessionId, int limit) throws Exception;

    List<AgentRunRecord> listRecoverable(int limit) throws Exception;

    List<AgentRunRecord> listActiveBefore(long beforeEpochMillis, int limit) throws Exception;

    void markStaleRuns(long beforeEpochMillis, long now) throws Exception;

    void appendEvent(AgentRunEventRecord event) throws Exception;

    List<AgentRunEventRecord> listEvents(String runId) throws Exception;

    void saveToolCall(ToolCallRecord record) throws Exception;

    List<ToolCallRecord> listToolCalls(String runId) throws Exception;

    void saveSubagentRun(SubagentRunRecord record) throws Exception;

    List<SubagentRunRecord> listSubagents(String parentRunId) throws Exception;

    void saveRecovery(RunRecoveryRecord record) throws Exception;

    List<RunRecoveryRecord> listRecoveries(String runId) throws Exception;

    void pruneBefore(long beforeEpochMillis) throws Exception;
}
