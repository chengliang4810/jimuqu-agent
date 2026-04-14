package com.jimuqu.claw.agent.store.file;

import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.store.RunStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import org.noear.solon.Utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileRunStore implements RunStore {
    private final WorkspaceLayout workspaceLayout;

    public FileRunStore(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    @Override
    public RunRecord get(String runId) {
        return JsonFileStoreSupport.read(workspaceLayout.runFile(runId), RunRecord.class);
    }

    @Override
    public void save(RunRecord record) {
        JsonFileStoreSupport.write(workspaceLayout.runFile(record.getRunId()), record);
    }

    @Override
    public List<RunRecord> listByParentRunId(String parentRunId) {
        List<RunRecord> results = new ArrayList<RunRecord>();
        for (Path path : JsonFileStoreSupport.listJsonFiles(workspaceLayout.runsDir())) {
            RunRecord record = JsonFileStoreSupport.read(path, RunRecord.class);
            if (record != null && Utils.isNotEmpty(parentRunId) && parentRunId.equals(record.getParentRunId())) {
                results.add(record);
            }
        }

        return results;
    }
}
