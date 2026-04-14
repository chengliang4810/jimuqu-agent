package com.jimuqu.claw.agent.workspace;

import cn.hutool.core.io.FileUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

public class WorkspacePathGuardTest {
    @Test
    public void resolvesInsideWorkspaceAndBlocksTraversal() {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/path-guard")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            WorkspacePathGuard guard = new WorkspacePathGuard(layout);

            Path resolved = guard.resolveWorkspacePath("docs/readme.md");
            Assertions.assertTrue(resolved.startsWith(root));
            Assertions.assertEquals(root.resolve("docs/readme.md").normalize(), resolved);

            IllegalArgumentException error = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> guard.resolveWorkspacePath("..\\outside.txt"));
            Assertions.assertTrue(error.getMessage().contains("workspace boundary"));
        } finally {
            FileUtil.del(root.toFile());
        }
    }
}
