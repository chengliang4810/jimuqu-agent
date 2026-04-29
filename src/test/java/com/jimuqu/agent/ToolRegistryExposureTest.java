package com.jimuqu.agent;

import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolRegistryExposureTest {
    @Test
    void shouldExposeBuiltinSearchTools() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        List<String> names = env.gatewayService == null ? java.util.Collections.<String>emptyList() : null;
        names = env.toolRegistry.listToolNames();

        assertThat(names).contains(
                "codesearch", "websearch", "webfetch",
                "file_read", "file_write", "file_list", "file_delete",
                "execute_shell", "execute_python", "execute_js", "get_current_time",
                "skills_list", "skill_view", "skill_manage",
                "skills_hub_search", "skills_hub_install", "skills_hub_tap"
        );
        assertThat(names).doesNotContain("exists_cmd", "list_files", "read_file", "write_file", "patch", "search_files");

        List<Object> tools = env.toolRegistry.resolveEnabledTools("MEMORY:room-1:user-1");
        String joined = tools.toString();
        assertThat(joined).contains("CodeSearchTool");
        assertThat(joined).contains("WebsearchTool");
        assertThat(joined).contains("WebfetchTool");
        assertThat(joined).contains("FileReadWriteSkill");
        assertThat(joined).contains("ShellSkill");
        assertThat(joined).contains("PythonSkill");
        assertThat(joined).contains("NodejsSkill");
        assertThat(joined).contains("SystemClockSkill");
        assertThat(joined).contains("SkillsListTool");
    }

    @Test
    void shouldDropFileSkillWhenAllFileToolsAreDisabled() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.toolRegistry.disableTools("MEMORY:room-1:user-1", java.util.Arrays.asList(
                "file_read", "file_write", "file_list", "file_delete"
        ));

        String joined = env.toolRegistry.resolveEnabledTools("MEMORY:room-1:user-1").toString();

        assertThat(joined).doesNotContain("FileReadWriteSkill");
    }
}

