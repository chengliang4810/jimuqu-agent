package com.jimuqu.claw.agent.tool;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.job.JobExecutionService;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.store.JobStore;
import com.jimuqu.claw.agent.store.ProcessStore;
import com.jimuqu.claw.agent.store.file.FileJobStore;
import com.jimuqu.claw.agent.store.file.FileProcessStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.agent.workspace.WorkspacePathGuard;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.skill.FileSkillCatalog;
import com.jimuqu.claw.skill.SkillCatalog;
import com.jimuqu.claw.skill.SkillManagerService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatSession;
import org.noear.solon.ai.chat.tool.FunctionTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultToolRegistryTest {
    @Test
    public void todoMemoryAndCronjobToolsWorkOnFileBackedState() throws Throwable {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/tool-registry")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            WorkspacePathGuard guard = new WorkspacePathGuard(layout);
            SkillCatalog skillCatalog = new FileSkillCatalog(layout);
            SkillManagerService skillManagerService = new SkillManagerService(layout, skillCatalog);
            JobStore jobStore = new FileJobStore(layout);
            ProcessStore processStore = new FileProcessStore(layout);
            ClawProperties properties = new ClawProperties();
            properties.getTerminal().setAllowedCommands(Collections.<String>emptyList());

            DefaultToolRegistry registry = new DefaultToolRegistry(
                    layout,
                    guard,
                    skillCatalog,
                    skillManagerService,
                    jobStore,
                    processStore,
                    properties,
                    new ArrayList<>());

            Map<String, Object> todoWriteArgs = new LinkedHashMap<String, Object>();
            todoWriteArgs.put("__sessionId", "sess-test");
            List<Map<String, Object>> todos = new ArrayList<Map<String, Object>>();
            Map<String, Object> todoItem = new LinkedHashMap<String, Object>();
            todoItem.put("id", "1");
            todoItem.put("content", "implement tool registry");
            todoItem.put("status", "in_progress");
            todos.add(todoItem);
            todoWriteArgs.put("todos", todos);

            Map<String, Object> todoWriteResult = invoke(registry.get("todo"), todoWriteArgs);
            Assertions.assertEquals(1, ((List<?>) todoWriteResult.get("todos")).size());
            Assertions.assertEquals(1, ((Map<?, ?>) todoWriteResult.get("summary")).get("in_progress"));

            Map<String, Object> memoryArgs = new LinkedHashMap<String, Object>();
            memoryArgs.put("action", "add");
            memoryArgs.put("target", "memory");
            memoryArgs.put("content", "workspace uses Solon and snack4");
            Map<String, Object> memoryResult = invoke(registry.get("memory"), memoryArgs);
            Assertions.assertEquals(1, memoryResult.get("entry_count"));

            Map<String, Object> createJobArgs = new LinkedHashMap<String, Object>();
            createJobArgs.put("action", "create");
            createJobArgs.put("name", "daily-check");
            createJobArgs.put("prompt", "check runtime");
            createJobArgs.put("schedule", "10s");
            Map<String, Object> createJobResult = invoke(registry.get("cronjob"), createJobArgs);
            Assertions.assertEquals(Boolean.TRUE, createJobResult.get("success"));
            Assertions.assertNotNull(((Map<?, ?>) createJobResult.get("job")).get("job_id"));
            Assertions.assertNotNull(((Map<?, ?>) createJobResult.get("job")).get("next_run_at"));

            Map<String, Object> listJobArgs = new LinkedHashMap<String, Object>();
            listJobArgs.put("action", "list");
            Map<String, Object> listJobResult = invoke(registry.get("cronjob"), listJobArgs);
            Assertions.assertEquals(1, listJobResult.get("count"));

            Map<String, Object> ambiguousAddOne = new LinkedHashMap<String, Object>();
            ambiguousAddOne.put("action", "add");
            ambiguousAddOne.put("target", "memory");
            ambiguousAddOne.put("content", "duplicate memory entry A");
            invoke(registry.get("memory"), ambiguousAddOne);

            Map<String, Object> ambiguousAddTwo = new LinkedHashMap<String, Object>();
            ambiguousAddTwo.put("action", "add");
            ambiguousAddTwo.put("target", "memory");
            ambiguousAddTwo.put("content", "duplicate memory entry B");
            invoke(registry.get("memory"), ambiguousAddTwo);

            Map<String, Object> ambiguousRemove = new LinkedHashMap<String, Object>();
            ambiguousRemove.put("action", "remove");
            ambiguousRemove.put("target", "memory");
            ambiguousRemove.put("old_text", "duplicate memory entry");
            Map<String, Object> ambiguousResult = invoke(registry.get("memory"), ambiguousRemove);
            Assertions.assertEquals(Boolean.FALSE, ambiguousResult.get("success"));
            Assertions.assertTrue(String.valueOf(ambiguousResult.get("error")).contains("Multiple entries matched"));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void terminalAndProcessToolsManageForegroundAndBackgroundCommands() throws Throwable {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/tool-terminal")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            WorkspacePathGuard guard = new WorkspacePathGuard(layout);
            SkillCatalog skillCatalog = new FileSkillCatalog(layout);
            SkillManagerService skillManagerService = new SkillManagerService(layout, skillCatalog);
            JobStore jobStore = new FileJobStore(layout);
            ProcessStore processStore = new FileProcessStore(layout);
            ClawProperties properties = new ClawProperties();
            properties.getTerminal().setAllowedCommands(Collections.<String>emptyList());

            DefaultToolRegistry registry = new DefaultToolRegistry(
                    layout,
                    guard,
                    skillCatalog,
                    skillManagerService,
                    jobStore,
                    processStore,
                    properties,
                    new ArrayList<>());

            Map<String, Object> terminalArgs = new LinkedHashMap<String, Object>();
            terminalArgs.put("command", foregroundCommand());
            terminalArgs.put("timeout_ms", Integer.valueOf(5000));
            Map<String, Object> terminalResult = invoke(registry.get("terminal"), terminalArgs);
            Assertions.assertEquals(Boolean.TRUE, terminalResult.get("success"));
            Assertions.assertTrue(String.valueOf(terminalResult.get("stdout")).contains("foreground-ok"));

            Map<String, Object> backgroundArgs = new LinkedHashMap<String, Object>();
            backgroundArgs.put("command", backgroundCommand());
            backgroundArgs.put("background", Boolean.TRUE);
            Map<String, Object> backgroundResult = invoke(registry.get("terminal"), backgroundArgs);
            Assertions.assertEquals(Boolean.TRUE, backgroundResult.get("success"));
            String sessionId = String.valueOf(((Map<?, ?>) backgroundResult.get("process")).get("session_id"));

            Map<String, Object> listArgs = new LinkedHashMap<String, Object>();
            listArgs.put("action", "list");
            Map<String, Object> listResult = invoke(registry.get("process"), listArgs);
            Assertions.assertTrue(((Number) listResult.get("count")).intValue() >= 1);

            Map<String, Object> waitArgs = new LinkedHashMap<String, Object>();
            waitArgs.put("action", "wait");
            waitArgs.put("session_id", sessionId);
            waitArgs.put("timeout_ms", Integer.valueOf(5000));
            Map<String, Object> waitResult = invoke(registry.get("process"), waitArgs);
            Assertions.assertEquals(Boolean.TRUE, waitResult.get("success"));
            Assertions.assertEquals(Boolean.FALSE, waitResult.get("timed_out"));
            Assertions.assertEquals("finished", ((Map<?, ?>) waitResult.get("process")).get("status"));
            Assertions.assertTrue(String.valueOf(waitResult.get("stdout")).contains("background-done"));

            Map<String, Object> logArgs = new LinkedHashMap<String, Object>();
            logArgs.put("action", "log");
            logArgs.put("session_id", sessionId);
            logArgs.put("limit", Integer.valueOf(20));
            Map<String, Object> logResult = invoke(registry.get("process"), logArgs);
            Map<?, ?> stdout = (Map<?, ?>) logResult.get("stdout");
            String combinedOutput = String.valueOf(waitResult.get("stdout")) + "\n" + String.valueOf(stdout.get("text"));
            Assertions.assertTrue(combinedOutput.contains("background-done"));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void delegateTaskBuildsChildRunRequestAndReturnsChildResult() throws Throwable {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/tool-delegate")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            WorkspacePathGuard guard = new WorkspacePathGuard(layout);
            SkillCatalog skillCatalog = new FileSkillCatalog(layout);
            SkillManagerService skillManagerService = new SkillManagerService(layout, skillCatalog);
            JobStore jobStore = new FileJobStore(layout);
            ProcessStore processStore = new FileProcessStore(layout);
            ClawProperties properties = new ClawProperties();
            properties.getTerminal().setAllowedCommands(Collections.<String>emptyList());

            CapturingRuntimeService runtimeService = new CapturingRuntimeService();
            DefaultToolRegistry registry = new DefaultToolRegistry(
                    layout,
                    guard,
                    skillCatalog,
                    skillManagerService,
                    jobStore,
                    processStore,
                    properties,
                    new ArrayList<>(),
                    () -> runtimeService);

            Map<String, Object> args = new LinkedHashMap<String, Object>();
            args.put(ChatSession.ATTR_SESSIONID, "sess-parent");
            args.put("__runId", "run-parent");
            args.put("__delegateDepth", Integer.valueOf(0));
            args.put("__platform", "wecom");
            args.put("__chatId", "chat-1");
            args.put("__threadId", "thread-9");
            args.put("__userId", "user-3");
            args.put("__workspaceRoot", root.toString());
            args.put("task", "Summarize the workspace state.");
            args.put("context", "Focus on runtime files.");

            Map<String, Object> result = invoke(registry.get("delegate_task"), args);
            Assertions.assertEquals(Boolean.TRUE, result.get("success"));
            Assertions.assertEquals("child-complete", result.get("response"));
            Assertions.assertEquals(1, result.get("delegate_depth"));

            Assertions.assertNotNull(runtimeService.capturedRequest);
            Assertions.assertEquals("run-parent", runtimeService.capturedRequest.getParentRunId());
            Assertions.assertEquals("delegate_task", runtimeService.capturedRequest.getSource());
            Assertions.assertTrue(runtimeService.capturedRequest.getUserMessage().contains("Focus on runtime files."));
            Assertions.assertTrue(runtimeService.capturedRequest.getUserMessage().contains("Summarize the workspace state."));
            Assertions.assertEquals("wecom", runtimeService.capturedRequest.getSessionContext().getPlatform());
            Assertions.assertEquals("chat-1", runtimeService.capturedRequest.getSessionContext().getChatId());
            Assertions.assertNotEquals("sess-parent", runtimeService.capturedRequest.getSessionContext().getSessionId());
            Assertions.assertEquals(1, runtimeService.capturedRequest.getSessionContext().getMetadata().get("delegateDepth"));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void delegateTaskEnforcesDepthLimitBeforeCallingRuntime() throws Throwable {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/tool-delegate-limit")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            WorkspacePathGuard guard = new WorkspacePathGuard(layout);
            SkillCatalog skillCatalog = new FileSkillCatalog(layout);
            SkillManagerService skillManagerService = new SkillManagerService(layout, skillCatalog);
            JobStore jobStore = new FileJobStore(layout);
            ProcessStore processStore = new FileProcessStore(layout);
            ClawProperties properties = new ClawProperties();
            properties.getRuntime().setDelegateDepthLimit(Integer.valueOf(1));

            CapturingRuntimeService runtimeService = new CapturingRuntimeService();
            DefaultToolRegistry registry = new DefaultToolRegistry(
                    layout,
                    guard,
                    skillCatalog,
                    skillManagerService,
                    jobStore,
                    processStore,
                    properties,
                    new ArrayList<>(),
                    () -> runtimeService);

            Map<String, Object> args = new LinkedHashMap<String, Object>();
            args.put(ChatSession.ATTR_SESSIONID, "sess-parent");
            args.put("__runId", "run-parent");
            args.put("__delegateDepth", Integer.valueOf(1));
            args.put("task", "Too deep");

            Map<String, Object> result = invoke(registry.get("delegate_task"), args);
            Assertions.assertEquals(Boolean.FALSE, result.get("success"));
            Assertions.assertTrue(String.valueOf(result.get("error")).contains("Delegate depth limit"));
            Assertions.assertNull(runtimeService.capturedRequest);
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void cronjobRunTriggersRuntimeAndReturnsRunMetadata() throws Throwable {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/tool-cronjob-run")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            WorkspacePathGuard guard = new WorkspacePathGuard(layout);
            SkillCatalog skillCatalog = new FileSkillCatalog(layout);
            SkillManagerService skillManagerService = new SkillManagerService(layout, skillCatalog);
            JobStore jobStore = new FileJobStore(layout);
            ProcessStore processStore = new FileProcessStore(layout);
            ClawProperties properties = new ClawProperties();

            CapturingRuntimeService runtimeService = new CapturingRuntimeService();
            JobExecutionService jobExecutionService = new JobExecutionService(jobStore, layout, () -> runtimeService);
            DefaultToolRegistry registry = new DefaultToolRegistry(
                    layout,
                    guard,
                    skillCatalog,
                    skillManagerService,
                    jobStore,
                    processStore,
                    properties,
                    new ArrayList<>(),
                    jobExecutionService,
                    () -> runtimeService);

            Map<String, Object> createArgs = new LinkedHashMap<String, Object>();
            createArgs.put("action", "create");
            createArgs.put("name", "nightly");
            createArgs.put("prompt", "check routes");
            createArgs.put("schedule", "10s");
            createArgs.put("deliver", "feishu:chat-home:thread-3");
            Map<String, Object> createResult = invoke(registry.get("cronjob"), createArgs);
            String jobId = String.valueOf(((Map<?, ?>) createResult.get("job")).get("job_id"));

            Map<String, Object> runArgs = new LinkedHashMap<String, Object>();
            runArgs.put("action", "run");
            runArgs.put("job_id", jobId);
            Map<String, Object> runResult = invoke(registry.get("cronjob"), runArgs);

            Assertions.assertEquals(Boolean.TRUE, runResult.get("success"));
            Assertions.assertEquals("child-complete", runResult.get("response"));
            Assertions.assertEquals(RunStatus.SUCCEEDED.name().toLowerCase(), ((Map<?, ?>) runResult.get("run")).get("status"));
            Assertions.assertNotNull(runtimeService.capturedRequest);
            Assertions.assertEquals("cronjob", runtimeService.capturedRequest.getSource());
            Assertions.assertEquals("feishu", runtimeService.capturedRequest.getReplyRoute().getPlatform());
            Assertions.assertEquals("chat-home", runtimeService.capturedRequest.getReplyRoute().getChatId());
            Assertions.assertEquals("thread-3", runtimeService.capturedRequest.getReplyRoute().getThreadId());

            Assertions.assertNotNull(jobStore.get(jobId).getLastRunAt());
            Assertions.assertTrue(String.valueOf(jobStore.get(jobId).getLastResultSummary()).contains("child-complete"));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void patchToolSupportsV4aPatchMode() throws Throwable {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/tool-patch-v4a")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            WorkspacePathGuard guard = new WorkspacePathGuard(layout);
            SkillCatalog skillCatalog = new FileSkillCatalog(layout);
            SkillManagerService skillManagerService = new SkillManagerService(layout, skillCatalog);
            JobStore jobStore = new FileJobStore(layout);
            ProcessStore processStore = new FileProcessStore(layout);
            ClawProperties properties = new ClawProperties();

            DefaultToolRegistry registry = new DefaultToolRegistry(
                    layout,
                    guard,
                    skillCatalog,
                    skillManagerService,
                    jobStore,
                    processStore,
                    properties,
                    new ArrayList<>());

            FileUtil.writeUtf8String("alpha\nbeta\ngamma\n", layout.getRoot().resolve("notes.txt").toFile());

            Map<String, Object> args = new LinkedHashMap<String, Object>();
            args.put("mode", "patch");
            args.put("patch",
                    "*** Begin Patch\n"
                            + "*** Update File: notes.txt\n"
                            + "@@\n"
                            + " alpha\n"
                            + "-beta\n"
                            + "+delta\n"
                            + " gamma\n"
                            + "*** Add File: extra.txt\n"
                            + "+created by patch\n"
                            + "*** End Patch");

            Map<String, Object> result = invoke(registry.get("patch"), args);
            Assertions.assertEquals(Boolean.TRUE, result.get("success"));
            Assertions.assertEquals(2, result.get("operations"));
            Assertions.assertTrue(FileUtil.readUtf8String(layout.getRoot().resolve("notes.txt").toFile()).contains("delta"));
            Assertions.assertEquals("created by patch", FileUtil.readUtf8String(layout.getRoot().resolve("extra.txt").toFile()));
        } finally {
            deleteTree(root);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoke(FunctionTool tool, Map<String, Object> args) throws Throwable {
        return (Map<String, Object>) tool.handle(args);
    }

    private String foregroundCommand() {
        return isWindows()
                ? "Write-Output foreground-ok"
                : "printf 'foreground-ok\\n'";
    }

    private String backgroundCommand() {
        return isWindows()
                ? "Write-Output background-ready; Start-Sleep -Milliseconds 200; Write-Output background-done"
                : "printf 'background-ready\\n'; sleep 0.2; printf 'background-done\\n'";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private void deleteTree(Path root) {
        if (root == null) {
            return;
        }

        try {
            Files.walk(root)
                    .sorted(Collections.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static class CapturingRuntimeService implements RuntimeService {
        private RunRequest capturedRequest;

        @Override
        public RunRecord handleInbound(ChannelInboundMessage inboundMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunRecord handleRequest(RunRequest request) {
            this.capturedRequest = request;
            return RunRecord.builder()
                    .runId(request.getRunId())
                    .parentRunId(request.getParentRunId())
                    .sessionId(request.getSessionContext().getSessionId())
                    .status(RunStatus.SUCCEEDED)
                    .responseText("child-complete")
                    .createdAt(request.getCreatedAt())
                    .build();
        }
    }
}
