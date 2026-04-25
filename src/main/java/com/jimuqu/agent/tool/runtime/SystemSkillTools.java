package com.jimuqu.agent.tool.runtime;

import lombok.RequiredArgsConstructor;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.skills.sys.NodejsSkill;
import org.noear.solon.ai.skills.sys.PythonSkill;
import org.noear.solon.ai.skills.sys.ShellSkill;

/**
 * Single-method wrappers around Solon sys skills so tool switches do not expose sibling tools.
 */
public final class SystemSkillTools {
    private SystemSkillTools() {
    }

    @RequiredArgsConstructor
    public static class ExistsCmdTool {
        private final ShellSkill delegate;

        @ToolMapping(name = "exists_cmd", description = "Check whether a command is available on the local system.")
        public boolean existsCmd(@Param("cmd") String cmd) {
            return delegate.existsCmd(cmd);
        }
    }

    @RequiredArgsConstructor
    public static class ListFilesTool {
        private final ShellSkill delegate;

        @ToolMapping(name = "list_files", description = "List files in the configured runtime working directory.")
        public String listFiles() {
            return delegate.listFiles();
        }
    }

    @RequiredArgsConstructor
    public static class ExecuteShellTool {
        private final ShellSkill delegate;

        @ToolMapping(name = "execute_shell", description = "Execute a local shell command or script and return stdout/stderr.")
        public String execute(@Param("code") String code) {
            return delegate.execute(code);
        }
    }

    @RequiredArgsConstructor
    public static class ExecutePythonTool {
        private final PythonSkill delegate;

        @ToolMapping(name = "execute_python", description = "Execute Python code and return output.")
        public String execute(@Param("code") String code) {
            return delegate.execute(code);
        }
    }

    @RequiredArgsConstructor
    public static class ExecuteJsTool {
        private final NodejsSkill delegate;

        @ToolMapping(name = "execute_js", description = "Execute Node.js JavaScript code and return output.")
        public String execute(@Param("code") String code) {
            return delegate.execute(code);
        }
    }
}
