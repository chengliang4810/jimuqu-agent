package com.jimuqu.claw.agent.store;

import com.jimuqu.claw.agent.runtime.model.ManagedProcessRecord;

import java.util.List;

public interface ProcessStore {
    ManagedProcessRecord get(String processId);

    List<ManagedProcessRecord> list();

    void save(ManagedProcessRecord record);
}
