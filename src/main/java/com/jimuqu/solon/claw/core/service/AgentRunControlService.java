package com.jimuqu.solon.claw.core.service;

import com.jimuqu.solon.claw.core.model.AgentRunStopResult;

/** Controls active Agent runs without exposing engine implementation details. */
public interface AgentRunControlService {
    /** Request cancellation of the active run for a gateway source. */
    AgentRunStopResult stop(String sourceKey);

    /** Whether the gateway source currently has an active run. */
    boolean isRunning(String sourceKey);

    /** Whether any source currently has an active run. */
    default boolean hasRunningRuns() {
        return false;
    }

    /** Last time any run finished. A zero value means no completed run is known. */
    default long lastRunFinishedAt() {
        return 0L;
    }
}
