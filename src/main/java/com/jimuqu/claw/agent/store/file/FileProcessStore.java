package com.jimuqu.claw.agent.store.file;

import com.jimuqu.claw.agent.runtime.model.ManagedProcessRecord;
import com.jimuqu.claw.agent.store.ProcessStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileProcessStore implements ProcessStore {
    private final WorkspaceLayout workspaceLayout;

    public FileProcessStore(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    @Override
    public ManagedProcessRecord get(String processId) {
        return JsonFileStoreSupport.read(workspaceLayout.processFile(processId), ManagedProcessRecord.class);
    }

    @Override
    public List<ManagedProcessRecord> list() {
        List<ManagedProcessRecord> results = new ArrayList<ManagedProcessRecord>();
        for (Path path : JsonFileStoreSupport.listJsonFiles(workspaceLayout.processesDir())) {
            ManagedProcessRecord record = JsonFileStoreSupport.read(path, ManagedProcessRecord.class);
            if (record != null) {
                results.add(record);
            }
        }

        return results;
    }

    @Override
    public void save(ManagedProcessRecord record) {
        JsonFileStoreSupport.write(workspaceLayout.processFile(record.getProcessId()), record);
    }
}
