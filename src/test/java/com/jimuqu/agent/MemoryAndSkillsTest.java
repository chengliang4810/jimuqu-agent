package com.jimuqu.agent;

import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.tool.runtime.SkillTools;
import com.jimuqu.agent.support.FakeLlmGateway;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryAndSkillsTest {
    @Test
    void shouldSupportCategorizedSkillsAndDefaultVisibility() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        env.localSkillService.createSkill("root-skill", null, skill("root-skill", "root skill"));
        env.localSkillService.createSkill("deploy", "ops", skill("deploy", "ops skill"));

        assertThat(env.localSkillService.listSkillNames()).contains("root-skill", "ops/deploy");
        assertThat(env.localSkillService.viewSkill("ops/deploy", null).getContent()).contains("ops skill");
        assertThat(env.localSkillService.renderSkillIndexPrompt("MEMORY:room:user")).contains("ops/deploy");

        env.localSkillService.disable("MEMORY:room:user", "root-skill");
        assertThat(env.localSkillService.renderSkillIndexPrompt("MEMORY:room:user")).doesNotContain("root-skill");
    }

    @Test
    void shouldFreezeMemorySnapshotUntilNewSession() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FakeLlmGateway fake = (FakeLlmGateway) env.llmGateway;

        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
        env.send("admin-chat", "admin-user", "first round");
        assertThat(fake.lastSystemPrompt).doesNotContain("冻结测试记忆");

        env.memoryService.add("memory", "冻结测试记忆");
        env.send("admin-chat", "admin-user", "second round");
        assertThat(fake.lastSystemPrompt).doesNotContain("冻结测试记忆");

        env.send("admin-chat", "admin-user", "/new");
        env.send("admin-chat", "admin-user", "third round");
        assertThat(fake.lastSystemPrompt).contains("冻结测试记忆");
    }

    @Test
    void shouldRejectTransientMemoryEntries() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        String response = env.memoryService.add("memory", "本会话临时 rollback TODO，稍后再删");

        assertThat(response).contains("不会写入长期记忆");
        assertThat(env.memoryService.read("memory")).isBlank();
    }

    @Test
    void shouldReturnRecoverableSkillToolErrorsInsteadOfThrowing() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.localSkillService.createSkill("demo-skill", null, skill("demo-skill", "demo"));
        SkillTools tools = new SkillTools(env.localSkillService, env.checkpointService, env.sessionRepository, "MEMORY:room:user");

        String missingSkill = tools.skillView("missing-skill", null);
        String invalidPath = tools.skillView("demo-skill", "../outside.txt");

        assertThat(missingSkill).contains("\"success\":false");
        assertThat(missingSkill).contains("Skill not found");
        assertThat(invalidPath).contains("\"success\":false");
        assertThat(invalidPath).contains("Invalid skill file path");
    }

    private String skill(String name, String description) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# Steps\n- example\n";
    }
}
