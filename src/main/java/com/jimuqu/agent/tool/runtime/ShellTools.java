package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ShellTools 实现。
 */
@RequiredArgsConstructor
public class ShellTools {
    private final ProcessRegistry processRegistry;
    private final Set<String> approvals = Collections.synchronizedSet(new LinkedHashSet<String>());

    @ToolMapping(name = "terminal", description = "Execute a shell command in a working directory. Dangerous commands require approval.")
    public String terminal(@Param(name = "command", description = "要执行的 shell 命令") String command,
                           @Param(name = "workingDir", description = "可选工作目录", required = false) String workingDir) throws Exception {
        if (isDangerous(command) && approvals.contains(command) == false) {
            return "Command blocked by approval policy. Use approval tool first: " + command;
        }

        ProcessBuilder processBuilder = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
        if (StrUtil.isNotBlank(workingDir)) {
            processBuilder.directory(new File(workingDir));
        }

        Process process = processBuilder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (finished == false) {
            process.destroyForcibly();
            return "Command timed out";
        }

        return read(process.getInputStream()) + read(process.getErrorStream());
    }

    @ToolMapping(name = "process", description = "Manage background processes. action can be list, start, or stop.")
    public String process(@Param(name = "action", description = "list、start、stop") String action,
                          @Param(name = "value", description = "动作附带值，例如命令或进程 ID", required = false) String value) throws Exception {
        if ("list".equalsIgnoreCase(action)) {
            StringBuilder buffer = new StringBuilder();
            for (Map.Entry<String, Process> entry : processRegistry.snapshot().entrySet()) {
                buffer.append(entry.getKey()).append('\n');
            }
            return buffer.length() == 0 ? "No tracked processes" : buffer.toString();
        }

        if ("stop".equalsIgnoreCase(action)) {
            return processRegistry.stop(value) ? "Stopped: " + value : "Unknown process: " + value;
        }

        if ("start".equalsIgnoreCase(action)) {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command", value).start();
            return "Started process: " + processRegistry.add(process);
        }

        return "Unsupported process action: " + action;
    }

    @ToolMapping(name = "execute_code", description = "Execute temporary code in powershell or python. language can be powershell or python.")
    public String executeCode(@Param(name = "language", description = "powershell 或 python") String language,
                              @Param(name = "code", description = "要执行的代码文本") String code,
                              @Param(name = "workingDir", description = "可选工作目录", required = false) String workingDir) throws Exception {
        String suffix = "python".equalsIgnoreCase(language) ? ".py" : ".ps1";
        File tempFile = FileUtil.createTempFile("jimuqu-code-", suffix, true);
        FileUtil.writeUtf8String(code, tempFile);
        String command;
        if ("python".equalsIgnoreCase(language)) {
            command = "python \"" + tempFile.getAbsolutePath() + "\"";
        } else {
            command = "& \"" + tempFile.getAbsolutePath() + "\"";
        }
        return terminal(command, workingDir);
    }

    @ToolMapping(name = "approval", description = "Approve a previously blocked command string for the current process.")
    public String approval(@Param(name = "action", description = "approve 或 revoke") String action,
                           @Param(name = "target", description = "目标命令文本") String target) {
        if ("approve".equalsIgnoreCase(action)) {
            approvals.add(target);
            return "Approved command: " + target;
        }

        if ("revoke".equalsIgnoreCase(action)) {
            approvals.remove(target);
            return "Revoked command: " + target;
        }

        return "Unsupported approval action";
    }

    private boolean isDangerous(String command) {
        String normalized = StrUtil.nullToEmpty(command).toLowerCase();
        return normalized.contains("del ")
                || normalized.contains("remove-item")
                || normalized.contains("format ")
                || normalized.contains("shutdown")
                || normalized.contains("restart-computer")
                || normalized.contains("git reset --hard");
    }

    private String read(InputStream inputStream) throws Exception {
        return IoUtil.readUtf8(inputStream);
    }

    /**
     * `terminal` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class TerminalTool {
        private final ShellTools delegate;

        @ToolMapping(name = "terminal", description = "Execute a shell command in a working directory. Dangerous commands require approval.")
        public String terminal(@Param(name = "command", description = "要执行的 shell 命令") String command,
                               @Param(name = "workingDir", description = "可选工作目录", required = false) String workingDir) throws Exception {
            return delegate.terminal(command, workingDir);
        }
    }

    /**
     * `process` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class ProcessTool {
        private final ShellTools delegate;

        @ToolMapping(name = "process", description = "Manage background processes. action can be list, start, or stop.")
        public String process(@Param(name = "action", description = "list、start、stop") String action,
                              @Param(name = "value", description = "动作附带值，例如命令或进程 ID", required = false) String value) throws Exception {
            return delegate.process(action, value);
        }
    }

    /**
     * `execute_code` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class ExecuteCodeTool {
        private final ShellTools delegate;

        @ToolMapping(name = "execute_code", description = "Execute temporary code in powershell or python. language can be powershell or python.")
        public String executeCode(@Param(name = "language", description = "powershell 或 python") String language,
                                  @Param(name = "code", description = "要执行的代码文本") String code,
                                  @Param(name = "workingDir", description = "可选工作目录", required = false) String workingDir) throws Exception {
            return delegate.executeCode(language, code, workingDir);
        }
    }

    /**
     * `approval` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class ApprovalTool {
        private final ShellTools delegate;

        @ToolMapping(name = "approval", description = "Approve a previously blocked command string for the current process.")
        public String approval(@Param(name = "action", description = "approve 或 revoke") String action,
                               @Param(name = "target", description = "目标命令文本") String target) {
            return delegate.approval(action, target);
        }
    }
}
