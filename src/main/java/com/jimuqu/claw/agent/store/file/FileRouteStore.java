package com.jimuqu.claw.agent.store.file;

import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.agent.store.RouteStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;

public class FileRouteStore implements RouteStore {
    private final WorkspaceLayout workspaceLayout;

    public FileRouteStore(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    @Override
    public ReplyRoute get(String sessionId) {
        return JsonFileStoreSupport.read(workspaceLayout.routeFile(sessionId), ReplyRoute.class);
    }

    @Override
    public void save(String sessionId, ReplyRoute route) {
        JsonFileStoreSupport.write(workspaceLayout.routeFile(sessionId), route);
    }
}
