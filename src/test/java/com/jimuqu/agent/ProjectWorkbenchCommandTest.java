package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProjectWorkbenchCommandTest {
    @Test
    void shouldManageAgentsAndProjectTodos() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.projectService.setAutoDeliveryEnabledForTest(false);
        bootstrapAdmin(env);

        GatewayReply agentReply = env.send("admin-chat", "admin-user", "/agent create builder implementation worker");
        assertThat(agentReply.getContent()).contains("builder");

        GatewayReply initReply = env.send("admin-chat", "admin-user", "/project init Demo Project");
        assertThat(initReply.getContent()).contains("项目初始化草稿").contains("demo-project");

        GatewayReply confirmReply = env.send("admin-chat", "admin-user", "/project confirm");
        assertThat(confirmReply.getContent()).contains("项目已创建").contains("demo-project").contains("待办已进入 todo 阶段");

        GatewayReply todoReply = env.send("admin-chat", "admin-user", "/project todo add Build local project board");
        assertThat(todoReply.getContent()).contains("TODO-004").contains("Build local project board");

        GatewayReply splitReply = env.send("admin-chat", "admin-user", "/project split TODO-004 backend repository; frontend board");
        assertThat(splitReply.getContent()).contains("2");

        GatewayReply assignReply = env.send("admin-chat", "admin-user", "/project assign TODO-004 builder");
        assertThat(assignReply.getContent()).contains("builder");

        GatewayReply runReply = env.send("admin-chat", "admin-user", "/project run TODO-004.1");
        assertThat(runReply.getContent()).contains("review");

        GatewayReply doneReply = env.send("admin-chat", "admin-user", "/project review TODO-004.1 pass");
        assertThat(doneReply.getContent()).contains("done");

        GatewayReply boardReply = env.send("admin-chat", "admin-user", "/project board");
        assertThat(boardReply.getContent()).contains("todo").contains("review").contains("done");

        File projectMd = FileUtil.file(env.appConfig.getRuntime().getHome(), "projects", "demo-project", "PROJECT.md");
        assertThat(projectMd).exists();
        assertThat(FileUtil.readUtf8String(projectMd)).contains("Demo Project");
    }

    @Test
    void shouldDraftProjectFromRequirementAndStartAutoDeliveryAfterConfirm() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.projectService.setAutoDeliveryDelaysForTest(500L, 10L);
        bootstrapAdmin(env);

        GatewayReply draftReply = env.send("admin-chat", "admin-user", "/project init Build dashboard project init workflow with tests");
        assertThat(draftReply.getContent()).contains("项目初始化草稿").contains("/project confirm").contains("frontend-agent");

        GatewayReply confirmReply = env.send("admin-chat", "admin-user", "/project confirm");
        assertThat(confirmReply.getContent()).contains("项目已创建").contains("待办已进入 todo 阶段").contains("自动推进已开始");

        GatewayReply boardReply = env.send("admin-chat", "admin-user", "/project board");
        assertThat(boardReply.getContent()).contains("# todo").contains("implementation-agent").contains("verification-agent").contains("# done (0)");

        String deliveredBoard = waitForBoard(env, "admin-chat", "admin-user", "# done (4)", 3000L);
        assertThat(deliveredBoard).contains("# todo (0)").contains("# in_progress (0)").contains("# review (0)");

        GatewayReply agentReply = env.send("admin-chat", "admin-user", "/agent list");
        assertThat(agentReply.getContent()).contains("frontend-agent").contains("implementation-agent").contains("verification-agent");
    }

    @Test
    void shouldDraftChineseAnalysisReportProjectAndStartAutoDelivery() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.projectService.setAutoDeliveryDelaysForTest(500L, 10L);
        bootstrapAdmin(env);

        GatewayReply draftReply = env.send("admin-chat", "admin-user", "/project init 将开源项目：后端https://github.com/chengliang4810/jimuqu-admin.git 前端 https://github.com/chengliang4810/jimuqu-admin-ui.git 分析一下，分析完给我一份pdf报告");
        assertThat(draftReply.getContent())
                .contains("项目初始化草稿")
                .contains("标题：开源项目分析报告")
                .contains("标识：open-source-project-analysis-report")
                .contains("拉取并梳理后端仓库")
                .contains("拉取并梳理前端仓库")
                .contains("生成交付文档或 PDF 报告")
                .contains("确认：/project confirm");
        assertThat(draftReply.getContent()).doesNotContain("Project init draft").doesNotContain("Confirm scope and acceptance criteria");

        GatewayReply confirmReply = env.send("admin-chat", "admin-user", "/project confirm");
        assertThat(confirmReply.getContent()).contains("项目已创建").contains("待办已进入 todo 阶段").contains("自动推进已开始");

        GatewayReply boardReply = env.send("admin-chat", "admin-user", "/project board");
        assertThat(boardReply.getContent()).contains("# todo (").contains("# done (0)");

        String deliveredBoard = waitForBoard(env, "admin-chat", "admin-user", "# done (6)", 4000L);
        assertThat(deliveredBoard).contains("生成交付文档或 PDF 报告").contains("# todo (0)");
    }

    @Test
    void shouldBlockSecretLikeTodosAndResumeAfterAnswer() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.projectService.setAutoDeliveryEnabledForTest(false);
        bootstrapAdmin(env);

        env.send("admin-chat", "admin-user", "/project init Secret Demo");
        env.send("admin-chat", "admin-user", "/project confirm");
        env.send("admin-chat", "admin-user", "/project todo add Configure API key");

        GatewayReply runReply = env.send("admin-chat", "admin-user", "/project run TODO-004");
        assertThat(runReply.getContent()).contains("waiting_user").contains("执行者：");

        GatewayReply questionsReply = env.send("admin-chat", "admin-user", "/project questions");
        assertThat(questionsReply.getContent()).contains("API Key");
    }

    @Test
    void shouldValidateProjectDashboardInputs() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                env.projectService.createProjectFromDashboard(java.util.Collections.<String, Object>singletonMap("slug", "bad slug!"));
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project slug");

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<String, Object>();
        body.put("slug", "api-demo");
        body.put("title", "API Demo");
        java.util.Map<String, Object> project = env.projectService.createProjectFromDashboard(body);

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                env.projectService.createTodoFromDashboard(String.valueOf(project.get("id")), java.util.Collections.<String, Object>emptyMap());
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("todo title");

        java.util.Map<String, Object> todoBody = new java.util.LinkedHashMap<String, Object>();
        todoBody.put("title", "Implement API validation");
        java.util.Map<String, Object> todo = env.projectService.createTodoFromDashboard(String.valueOf(project.get("id")), todoBody);

        java.util.Map<String, Object> statusBody = new java.util.LinkedHashMap<String, Object>();
        statusBody.put("status", "not-a-status");
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() throws Throwable {
                env.projectService.updateTodoStatus(String.valueOf(project.get("id")), String.valueOf(todo.get("id")), statusBody);
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported todo status");
    }

    private void bootstrapAdmin(TestEnvironment env) throws Exception {
        env.send("admin-chat", "admin-user", "hello");
        env.send("admin-chat", "admin-user", "/pairing claim-admin");
    }

    private String waitForBoard(TestEnvironment env, String chatId, String userId, String expected, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        String content = "";
        while (System.currentTimeMillis() < deadline) {
            GatewayReply reply = env.send(chatId, userId, "/project board");
            content = reply.getContent();
            if (content.contains(expected)) {
                return content;
            }
            Thread.sleep(50L);
        }
        return content;
    }
}
