package com.jimuqu.claw.agent.store.file;

import com.jimuqu.claw.agent.store.DedupStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.support.Ids;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileDedupStore implements DedupStore {
    private final WorkspaceLayout workspaceLayout;

    public FileDedupStore(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    @Override
    public synchronized boolean markIfAbsent(String dedupKey) {
        Path file = workspaceLayout.dedupFile(Ids.hashKey(dedupKey));
        if (file.toFile().exists()) {
            return false;
        }

        Map<String, Object> marker = new LinkedHashMap<String, Object>();
        marker.put("dedupKey", dedupKey);
        marker.put("createdAt", Instant.now().toString());
        JsonFileStoreSupport.write(file, marker);
        return true;
    }

    @Override
    public boolean exists(String dedupKey) {
        return workspaceLayout.dedupFile(Ids.hashKey(dedupKey)).toFile().exists();
    }
}
