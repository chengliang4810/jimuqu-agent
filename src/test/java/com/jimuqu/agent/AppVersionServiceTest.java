package com.jimuqu.agent;

import com.jimuqu.agent.support.update.AppVersionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AppVersionServiceTest {
    @Test
    void shouldCompareSemanticVersions() {
        assertThat(AppVersionService.compareVersions("0.0.1", "0.0.2")).isLessThan(0);
        assertThat(AppVersionService.compareVersions("v0.1.0", "0.0.9")).isGreaterThan(0);
        assertThat(AppVersionService.compareVersions("1.2.0-beta", "1.2.0")).isEqualTo(0);
    }
}
