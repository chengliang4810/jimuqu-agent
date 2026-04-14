package com.jimuqu.claw.agent.store.file;

import com.jimuqu.claw.agent.runtime.model.JobRecord;
import com.jimuqu.claw.agent.store.JobStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.support.FileStoreSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileJobStore implements JobStore {
    private final WorkspaceLayout workspaceLayout;

    public FileJobStore(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    @Override
    public JobRecord get(String jobId) {
        return JsonFileStoreSupport.read(workspaceLayout.jobFile(jobId), JobRecord.class);
    }

    @Override
    public List<JobRecord> list() {
        List<JobRecord> results = new ArrayList<JobRecord>();
        for (Path path : JsonFileStoreSupport.listJsonFiles(workspaceLayout.jobsDir())) {
            JobRecord record = JsonFileStoreSupport.read(path, JobRecord.class);
            if (record != null) {
                results.add(record);
            }
        }

        return results;
    }

    @Override
    public void save(JobRecord record) {
        JsonFileStoreSupport.write(workspaceLayout.jobFile(record.getJobId()), record);
    }

    @Override
    public void remove(String jobId) {
        FileStoreSupport.deleteIfExists(workspaceLayout.jobFile(jobId));
    }
}
