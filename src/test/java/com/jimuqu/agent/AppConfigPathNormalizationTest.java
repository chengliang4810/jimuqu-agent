package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class AppConfigPathNormalizationTest {
    @Test
    void shouldResolveRuntimePathsAgainstWorkingDirectoryOnlyOnce() {
        AppConfig config = new AppConfig();
        config.getRuntime().setHome("runtime-live-onboarding");
        config.getRuntime().setContextDir("outside/context");
        config.getRuntime().setSkillsDir("outside/skills");
        config.getRuntime().setCacheDir("outside/cache");
        config.getRuntime().setStateDb("outside/state.db");
        config.getRuntime().setLogsDir("outside/logs");

        config.normalizePaths();

        String base = new File(System.getProperty("user.dir")).getAbsolutePath();
        assertThat(config.getRuntime().getHome()).isEqualTo(new File(base, "runtime-live-onboarding").getAbsolutePath());
        assertThat(config.getRuntime().getContextDir()).isEqualTo(new File(base, "runtime-live-onboarding/context").getAbsolutePath());
        assertThat(config.getRuntime().getSkillsDir()).isEqualTo(new File(base, "runtime-live-onboarding/skills").getAbsolutePath());
        assertThat(config.getRuntime().getCacheDir()).isEqualTo(new File(base, "runtime-live-onboarding/cache").getAbsolutePath());
        assertThat(config.getRuntime().getStateDb()).isEqualTo(new File(base, "runtime-live-onboarding/data/state.db").getAbsolutePath());
        assertThat(config.getRuntime().getLogsDir()).isEqualTo(new File(base, "runtime-live-onboarding/logs").getAbsolutePath());
    }

    @Test
    void shouldIgnoreExternalRuntimeChildPathOverrides() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-runtime-home").toFile();
        File outside = Files.createTempDirectory("jimuqu-runtime-outside").toFile();

        Props props = new Props();
        props.put("jimuqu.runtime.home", runtimeHome.getAbsolutePath());
        props.put("jimuqu.runtime.contextDir", new File(outside, "context").getAbsolutePath());
        props.put("jimuqu.runtime.skillsDir", new File(outside, "skills").getAbsolutePath());
        props.put("jimuqu.runtime.cacheDir", new File(outside, "cache").getAbsolutePath());
        props.put("jimuqu.runtime.stateDb", new File(outside, "state.db").getAbsolutePath());
        props.put("jimuqu.logging.dir", new File(outside, "logs").getAbsolutePath());

        AppConfig config = AppConfig.load(props);

        assertThat(config.getRuntime().getHome()).isEqualTo(runtimeHome.getAbsolutePath());
        assertThat(config.getRuntime().getContextDir()).isEqualTo(new File(runtimeHome, "context").getAbsolutePath());
        assertThat(config.getRuntime().getSkillsDir()).isEqualTo(new File(runtimeHome, "skills").getAbsolutePath());
        assertThat(config.getRuntime().getCacheDir()).isEqualTo(new File(runtimeHome, "cache").getAbsolutePath());
        assertThat(config.getRuntime().getStateDb()).isEqualTo(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        assertThat(config.getRuntime().getLogsDir()).isEqualTo(new File(runtimeHome, "logs").getAbsolutePath());
    }

    @Test
    void shouldUseSystemRuntimeHomeAsOnlyExternalRuntimePath() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-runtime-property-home").toFile();
        String previous = System.getProperty("jimuqu.runtime.home");
        try {
            System.setProperty("jimuqu.runtime.home", runtimeHome.getAbsolutePath());

            AppConfig config = AppConfig.load(new Props());

            assertThat(config.getRuntime().getHome()).isEqualTo(runtimeHome.getAbsolutePath());
            assertThat(config.getRuntime().getContextDir()).isEqualTo(new File(runtimeHome, "context").getAbsolutePath());
            assertThat(config.getRuntime().getStateDb()).isEqualTo(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
            assertThat(config.getRuntime().getLogsDir()).isEqualTo(new File(runtimeHome, "logs").getAbsolutePath());
        } finally {
            if (previous == null) {
                System.clearProperty("jimuqu.runtime.home");
            } else {
                System.setProperty("jimuqu.runtime.home", previous);
            }
        }
    }
}

