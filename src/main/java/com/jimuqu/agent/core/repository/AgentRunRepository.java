package com.jimuqu.agent.core.repository;

import com.jimuqu.agent.core.model.AgentRunEventRecord;
import com.jimuqu.agent.core.model.AgentRunRecord;

import java.util.List;

/**
 * Agent 运行轨迹仓储。
 */
public interface AgentRunRepository {
    void saveRun(AgentRunRecord record) throws Exception;

    AgentRunRecord findRun(String runId) throws Exception;

    List<AgentRunRecord> listBySession(String sessionId, int limit) throws Exception;

    void appendEvent(AgentRunEventRecord event) throws Exception;

    List<AgentRunEventRecord> listEvents(String runId) throws Exception;

    void pruneBefore(long beforeEpochMillis) throws Exception;
}
