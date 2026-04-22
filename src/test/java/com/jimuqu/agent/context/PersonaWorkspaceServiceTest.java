package com.jimuqu.agent.context;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.support.constants.ContextFileConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonaWorkspaceServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void readsAndWritesManagedFiles() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());

        assertThat(service.orderedKeys()).containsExactly(
                ContextFileConstants.KEY_AGENTS,
                ContextFileConstants.KEY_SOUL,
                ContextFileConstants.KEY_IDENTITY,
                ContextFileConstants.KEY_USER
        );
        assertThat(service.exists(ContextFileConstants.KEY_AGENTS)).isTrue();
        assertThat(service.read(ContextFileConstants.KEY_AGENTS)).contains("# AGENTS.md - 你的工作区");
        assertThat(service.read(ContextFileConstants.KEY_SOUL)).contains("# SOUL.md - 你是谁");
        assertThat(service.read(ContextFileConstants.KEY_IDENTITY)).contains("# IDENTITY.md - 我是谁？");
        assertThat(service.read(ContextFileConstants.KEY_USER)).contains("# USER.md - 关于你的用户");

        service.write(ContextFileConstants.KEY_AGENTS, "# AGENTS\n");

        assertThat(service.exists(ContextFileConstants.KEY_AGENTS)).isTrue();
        assertThat(service.read(ContextFileConstants.KEY_AGENTS)).isEqualTo("# AGENTS\n");
        assertThat(service.file(ContextFileConstants.KEY_AGENTS).getName()).isEqualTo(ContextFileConstants.FILE_AGENTS);
    }

    @Test
    void doesNotOverwriteExistingFilesWhenRecreated() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());
        service.write(ContextFileConstants.KEY_SOUL, "custom soul");

        PersonaWorkspaceService reloaded = new PersonaWorkspaceService(appConfig());

        assertThat(reloaded.read(ContextFileConstants.KEY_SOUL)).isEqualTo("custom soul");
    }

    @Test
    void restoresFileBackToTemplate() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());
        service.write(ContextFileConstants.KEY_USER, "custom user");

        service.restoreTemplate(ContextFileConstants.KEY_USER);

        assertThat(service.read(ContextFileConstants.KEY_USER)).contains("# USER.md - 关于你的用户");
        assertThat(service.read(ContextFileConstants.KEY_USER)).doesNotContain("custom user");
    }

    @Test
    void rejectsUnknownFileKeys() {
        PersonaWorkspaceService service = new PersonaWorkspaceService(appConfig());

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                service.read("unknown");
            }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    private AppConfig appConfig() {
        AppConfig config = new AppConfig();
        File contextDir = new File(tempDir.toFile(), "context");
        config.getRuntime().setContextDir(contextDir.getAbsolutePath());
        return config;
    }
}
