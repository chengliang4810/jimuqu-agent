package com.jimuqu.claw.agent.store;

import com.jimuqu.claw.agent.runtime.model.RunRecord;

import java.util.List;

public interface RunStore {
    RunRecord get(String runId);

    void save(RunRecord record);

    List<RunRecord> listByParentRunId(String parentRunId);
}
