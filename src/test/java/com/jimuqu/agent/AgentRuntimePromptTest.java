package com.jimuqu.agent;

import com.jimuqu.agent.support.FakeLlmGateway;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentRuntimePromptTest {
    @Test
    void shouldInjectAgentRuntimeBlockIntoSystemPrompt() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getGateway().setAllowAllUsers(true);
        env.conversationOrchestrator.handleIncoming(env.message("chat-a", "user-a", "你好"));

        FakeLlmGateway fake = (FakeLlmGateway) env.llmGateway;
        assertThat(fake.lastSystemPrompt)
                .contains("[Agent Runtime]")
                .contains("agent_name=Jimuqu Agent")
                .contains("platform=MEMORY")
                .contains("chat_id=chat-a")
                .contains("user_id=user-a")
                .contains("effective_provider=openai-responses")
                .contains("effective_model=gpt-5.4")
                .contains("react_summarization_enabled=true")
                .contains("enabled_tools=")
                .contains("shell_probe_policy=Prefer execute_shell for environment detection.")
                .contains("shell_probe_example=Use execute_shell with commands like: command -v git >/dev/null 2>&1 && git --version || echo git_missing");
    }
}
