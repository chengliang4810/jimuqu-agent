package com.jimuqu.claw.agent.workspace;

import java.nio.file.Path;
import java.nio.file.Paths;

public class WorkspacePathGuard {
    private final WorkspaceLayout workspaceLayout;

    public WorkspacePathGuard(WorkspaceLayout workspaceLayout) {
        this.workspaceLayout = workspaceLayout;
    }

    public Path resolveWorkspacePath(String rawPath) {
        Path input = Paths.get(rawPath);
        Path resolved = input.isAbsolute() ? input.normalize() : workspaceLayout.getRoot().resolve(input).normalize();
        if (!resolved.startsWith(workspaceLayout.getRoot())) {
            throw new IllegalArgumentException("Path escapes workspace boundary: " + rawPath);
        }
        return resolved;
    }

    public Path resolveSkillPath(String skillRelativePath) {
        Path resolved = workspaceLayout.skillsDir().resolve(skillRelativePath).normalize();
        if (!resolved.startsWith(workspaceLayout.skillsDir())) {
            throw new IllegalArgumentException("Path escapes skills boundary: " + skillRelativePath);
        }
        return resolved;
    }
}
