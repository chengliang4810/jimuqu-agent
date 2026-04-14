package com.jimuqu.claw.agent.store.file;

import com.jimuqu.claw.agent.runtime.model.SessionRecord;
import com.jimuqu.claw.agent.store.SessionStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;

public class FileSessionStore implements SessionStore {
    private final WorkspaceLayout workspaceLayout;

    public FileSessionStore(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    @Override
    public SessionRecord get(String sessionId) {
        return JsonFileStoreSupport.read(workspaceLayout.sessionMetaFile(sessionId), SessionRecord.class);
    }

    @Override
    public void save(SessionRecord record) {
        JsonFileStoreSupport.write(workspaceLayout.sessionMetaFile(record.getSessionId()), record);
    }
}
