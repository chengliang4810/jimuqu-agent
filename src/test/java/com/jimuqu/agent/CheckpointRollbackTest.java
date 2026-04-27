package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.support.TestEnvironment;
import com.jimuqu.agent.support.RuntimePathGuard;
import com.jimuqu.agent.tool.runtime.FileTools;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckpointRollbackTest {
    @Test
    void shouldRollbackLatestStructuredFileWrite() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");

        File file = FileUtil.file(env.appConfig.getRuntime().getCacheDir(), "sample.txt");
        FileTools fileTools = new FileTools(env.checkpointService, env.sessionRepository, "MEMORY:room-a:user-a", new RuntimePathGuard(env.appConfig));

        fileTools.writeFile(file.getAbsolutePath(), "v1");
        fileTools.writeFile(file.getAbsolutePath(), "v2");

        env.checkpointService.rollbackLatest("MEMORY:room-a:user-a");
        assertThat(FileUtil.readUtf8String(file)).isEqualTo("v1");
    }

    @Test
    void readFileShouldReturnReadableErrorForJarInternalPath() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FileTools fileTools = new FileTools(env.checkpointService, env.sessionRepository, "MEMORY:room-a:user-a", new RuntimePathGuard(env.appConfig));

        String result = fileTools.readFile(env.appConfig.getRuntime().getHome() + "/jimuqu-agent.jar!/org/noear/solon/core/USER.md");

        assertThat(result).contains("jar-internal paths are not disk files");
    }

    @Test
    void writeFileShouldReturnReadableErrorForJarInternalPath() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FileTools fileTools = new FileTools(env.checkpointService, env.sessionRepository, "MEMORY:room-a:user-a", new RuntimePathGuard(env.appConfig));

        String result = fileTools.writeFile(env.appConfig.getRuntime().getHome() + "/jimuqu-agent.jar!/org/noear/solon/core/USER.md", "content");

        assertThat(result).contains("jar-internal paths are not disk files");
        assertThat(FileUtil.file(env.appConfig.getRuntime().getHome(), "jimuqu-agent.jar!")).doesNotExist();
    }

    @Test
    void patchShouldReturnReadableErrorForJarInternalPath() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FileTools fileTools = new FileTools(env.checkpointService, env.sessionRepository, "MEMORY:room-a:user-a", new RuntimePathGuard(env.appConfig));

        String result = fileTools.patch(env.appConfig.getRuntime().getHome() + "/jimuqu-agent.jar!/org/noear/solon/core/USER.md", "old", "new");

        assertThat(result).contains("jar-internal paths are not disk files");
    }

    @Test
    void searchFilesShouldReturnReadableErrorForDisallowedRoot() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        FileTools fileTools = new FileTools(env.checkpointService, env.sessionRepository, "MEMORY:room-a:user-a", new RuntimePathGuard(env.appConfig));
        File outside = Files.createTempDirectory("jimuqu-agent-outside").toFile();

        String result = fileTools.searchFiles(outside.getAbsolutePath(), "anything");

        assertThat(result).contains("Cannot search files: Path is outside allowed roots");
        assertThat(result).contains("Allowed roots");
    }
}
