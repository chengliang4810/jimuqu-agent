package com.jimuqu.claw.agent.store;

import com.jimuqu.claw.agent.runtime.model.JobRecord;

import java.util.List;

public interface JobStore {
    JobRecord get(String jobId);

    List<JobRecord> list();

    void save(JobRecord record);

    void remove(String jobId);
}
