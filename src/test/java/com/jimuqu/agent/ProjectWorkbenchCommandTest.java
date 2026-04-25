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
        bootstrapAdmin(env);

        GatewayReply agentReply = env.send("admin-chat", "admin-user", "/agent create builder implementation worker");
        assertThat(agentReply.getContent()).contains("builder");

        GatewayReply initReply = env.send("admin-chat", "admin-user", "/project init demo Demo Project");
        assertThat(initReply.getContent()).contains("demo");

        GatewayReply todoReply = env.send("admin-chat", "admin-user", "/project todo add Build local project board");
        assertThat(todoReply.getContent()).contains("TODO-001");

        GatewayReply splitReply = env.send("admin-chat", "admin-user", "/project split TODO-001 backend repository; frontend board");
        assertThat(splitReply.getContent()).contains("2");

        GatewayReply assignReply = env.send("admin-chat", "admin-user", "/project assign TODO-001 builder");
        assertThat(assignReply.getContent()).contains("builder");

        GatewayReply runReply = env.send("admin-chat", "admin-user", "/project run TODO-001.1");
        assertThat(runReply.getContent()).contains("review");

        GatewayReply doneReply = env.send("admin-chat", "admin-user", "/project review TODO-001.1 pass");
        assertThat(doneReply.getContent()).contains("done");

        GatewayReply boardReply = env.send("admin-chat", "admin-user", "/project board");
        assertThat(boardReply.getContent()).contains("todo").contains("review").contains("done");

        File projectMd = FileUtil.file(env.appConfig.getRuntime().getHome(), "projects", "demo", "PROJECT.md");
        assertThat(projectMd).exists();
        assertThat(FileUtil.readUtf8String(projectMd)).contains("Demo Project");
    }

    @Test
    void shouldDraftProjectFromRequirementAndAutodeliverAfterConfirm() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        bootstrapAdmin(env);

        GatewayReply draftReply = env.send("admin-chat", "admin-user", "/project init Build dashboard project init workflow with tests");
        assertThat(draftReply.getContent()).contains("Project init draft").contains("/project confirm").contains("frontend-agent");

        GatewayReply confirmReply = env.send("admin-chat", "admin-user", "/project confirm");
        assertThat(confirmReply.getContent()).contains("Confirmed project").contains("Autopilot delivery").contains("Delivered: all todos are done.");

        GatewayReply boardReply = env.send("admin-chat", "admin-user", "/project board");
        assertThat(boardReply.getContent()).contains("# done").contains("implementation-agent").contains("verification-agent");

        GatewayReply agentReply = env.send("admin-chat", "admin-user", "/agent list");
        assertThat(agentReply.getContent()).contains("frontend-agent").contains("implementation-agent").contains("verification-agent");
    }

    @Test
    void shouldBlockSecretLikeTodosAndResumeAfterAnswer() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        bootstrapAdmin(env);

        env.send("admin-chat", "admin-user", "/project init secret-demo Secret Demo");
        env.send("admin-chat", "admin-user", "/project todo add Configure API key");

        GatewayReply runReply = env.send("admin-chat", "admin-user", "/project run TODO-001");
        assertThat(runReply.getContent()).contains("waiting_user").contains("User:");

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
}
