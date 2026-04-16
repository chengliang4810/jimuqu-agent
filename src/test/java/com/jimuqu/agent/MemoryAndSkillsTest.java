package com.jimuqu.agent;

import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.context.AsyncSkillLearningService;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.tool.runtime.SkillTools;
import com.jimuqu.agent.tool.runtime.MemoryTools;
import com.jimuqu.agent.support.FakeLlmGateway;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.message.ChatMessage;

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
    void shouldRefreshMemorySnapshotOnNextTurn() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FakeLlmGateway fake = (FakeLlmGateway) env.llmGateway;

        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
        env.send("admin-chat", "admin-user", "first round");
        assertThat(fake.lastSystemPrompt).doesNotContain("冻结测试记忆");

        env.memoryService.add("memory", "冻结测试记忆");
        env.send("admin-chat", "admin-user", "second round");
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

    @Test
    void shouldReturnStructuredJsonFromMemoryTool() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MemoryTools tools = new MemoryTools(env.memoryService);

        String addResult = tools.memory("add", "memory", "长期偏好：输出中文", null);
        String readResult = tools.memory("read", "memory", null, null);

        assertThat(addResult).contains("\"success\":true");
        assertThat(addResult).contains("\"action\":\"add\"");
        assertThat(readResult).contains("\"success\":true");
        assertThat(readResult).contains("\"content\"");
        assertThat(readResult).contains("长期偏好：输出中文");
    }

    @Test
    void shouldPatchExistingLearnedSkillInsteadOfSkipping() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getLearning().setToolCallThreshold(1);
        env.localSkillService.createSkill("repeatable-task", null, skill("repeatable-task", "demo"));

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room:user");
        session.setTitle("repeatable task");
        session.setCompressedSummary("已经验证的流程摘要");
        session.setNdjson(MessageSupport.toNdjson(java.util.Collections.singletonList(
                ChatMessage.ofTool("tool output", "tool", "1")
        )));
        env.sessionRepository.save(session);

        AsyncSkillLearningService learningService = new AsyncSkillLearningService(
                env.appConfig,
                env.sessionRepository,
                env.memoryService,
                env.localSkillService,
                env.checkpointService
        );
        GatewayMessage message = env.message("room", "user", "确认最终验证步骤");
        GatewayReply reply = GatewayReply.ok("done");
        learningService.schedulePostReplyLearning(session, message, reply);

        String content = waitSkillContent(env, "repeatable-task");
        assertThat(content).contains("已经验证的流程摘要");
        assertThat(content).contains("当前任务验证点");
    }

    private String waitSkillContent(TestEnvironment env, String name) throws Exception {
        long deadline = System.currentTimeMillis() + 2000L;
        while (System.currentTimeMillis() < deadline) {
            String content = env.localSkillService.viewSkill(name, null).getContent();
            if (content.contains("当前任务验证点")) {
                return content;
            }
            Thread.sleep(50L);
        }
        return env.localSkillService.viewSkill(name, null).getContent();
    }

    private String skill(String name, String description) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n\n# Steps\n- example\n";
    }
}
