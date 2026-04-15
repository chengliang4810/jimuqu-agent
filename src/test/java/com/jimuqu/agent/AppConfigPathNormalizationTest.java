package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

public class AppConfigPathNormalizationTest {
    @Test
    void shouldResolveRuntimePathsAgainstWorkingDirectoryOnlyOnce() {
        AppConfig config = new AppConfig();
        config.getRuntime().setHome("runtime-live-onboarding");
        config.getRuntime().setContextDir("runtime-live-onboarding/context");
        config.getRuntime().setSkillsDir("runtime-live-onboarding/skills");
        config.getRuntime().setCacheDir("runtime-live-onboarding/cache");
        config.getRuntime().setStateDb("runtime-live-onboarding/state.db");

        config.normalizePaths();

        String base = new File(System.getProperty("user.dir")).getAbsolutePath();
        assertThat(config.getRuntime().getHome()).isEqualTo(new File(base, "runtime-live-onboarding").getAbsolutePath());
        assertThat(config.getRuntime().getStateDb()).isEqualTo(new File(base, "runtime-live-onboarding/state.db").getAbsolutePath());
        assertThat(config.getRuntime().getContextDir()).isEqualTo(new File(base, "runtime-live-onboarding/context").getAbsolutePath());
    }
}

