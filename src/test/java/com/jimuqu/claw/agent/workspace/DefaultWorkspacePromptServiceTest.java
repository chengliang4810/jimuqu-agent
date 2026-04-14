package com.jimuqu.claw.agent.workspace;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.support.FileStoreSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;

public class DefaultWorkspacePromptServiceTest {
    @Test
    public void buildsPromptInExpectedOrder() {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/prompt-service")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            FileStoreSupport.writeUtf8Atomic(layout.agentsFile(), "agents");
            FileStoreSupport.writeUtf8Atomic(layout.userFile(), "user");
            FileStoreSupport.writeUtf8Atomic(layout.memoryFile(), "memory");
            FileStoreSupport.writeUtf8Atomic(layout.dailyMemoryFile(LocalDate.now().toString()), "daily");

            ClawProperties properties = new ClawProperties();
            properties.getRuntime().setSystemPromptResource(null);
            DefaultWorkspacePromptService service = new DefaultWorkspacePromptService(layout, properties);

            SessionContext context = SessionContext.builder()
                    .sessionId("sess-1")
                    .platform("wecom")
                    .chatId("chat-1")
                    .workspaceRoot(root.toString())
                    .build();

            String prompt = service.buildSystemPrompt(context);
            Assertions.assertTrue(prompt.contains("## AGENTS.md"));
            Assertions.assertTrue(prompt.contains("## USER.md"));
            Assertions.assertTrue(prompt.contains("## MEMORY.md"));
            Assertions.assertTrue(prompt.contains("## Daily Memory"));
            Assertions.assertTrue(prompt.contains("\"sessionId\":\"sess-1\""));

            Assertions.assertTrue(prompt.indexOf("## AGENTS.md") < prompt.indexOf("## USER.md"));
            Assertions.assertTrue(prompt.indexOf("## USER.md") < prompt.indexOf("## MEMORY.md"));
            Assertions.assertTrue(prompt.indexOf("## MEMORY.md") < prompt.indexOf("## Daily Memory"));
            Assertions.assertTrue(prompt.indexOf("## Daily Memory") < prompt.indexOf("## Session Context"));
        } finally {
            FileUtil.del(root.toFile());
        }
    }
}
