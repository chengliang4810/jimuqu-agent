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

        assertThat(names).contains("codesearch", "websearch", "webfetch");

        List<Object> tools = env.toolRegistry.resolveEnabledTools("MEMORY:room-1:user-1");
        String joined = tools.toString();
        assertThat(joined).contains("CodeSearchTool");
        assertThat(joined).contains("WebsearchTool");
        assertThat(joined).contains("WebfetchTool");
    }
}

