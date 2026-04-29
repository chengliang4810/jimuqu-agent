package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.model.AgentRunStopResult;

/**
 * Controls active Agent runs without exposing engine implementation details.
 */
public interface AgentRunControlService {
    /**
     * Request cancellation of the active run for a gateway source.
     */
    AgentRunStopResult stop(String sourceKey);

    /**
     * Whether the gateway source currently has an active run.
     */
    boolean isRunning(String sourceKey);
}
