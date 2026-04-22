package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
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

    @Test
    void shouldResolveCustomReleaseApiAndProxyFromSystemProperties() {
        AppConfig config = new AppConfig();
        AppVersionService service = new AppVersionService(config);
        String oldApi = System.getProperty("jimuqu.update.releaseApiUrl");
        String oldTagsApi = System.getProperty("jimuqu.update.tagsApiUrl");
        String oldProxy = System.getProperty("jimuqu.update.httpProxy");
        try {
            System.setProperty("jimuqu.update.releaseApiUrl", "https://mirror.example/releases/latest");
            System.setProperty("jimuqu.update.tagsApiUrl", "https://mirror.example/tags?per_page=5");
            System.setProperty("jimuqu.update.httpProxy", "http://127.0.0.1:7890");

            assertThat(service.releaseApiUrl()).isEqualTo("https://mirror.example/releases/latest");
            assertThat(service.tagsApiUrl()).isEqualTo("https://mirror.example/tags?per_page=5");
            assertThat(service.updateProxyUrl()).isEqualTo("http://127.0.0.1:7890");
        } finally {
            restore("jimuqu.update.releaseApiUrl", oldApi);
            restore("jimuqu.update.tagsApiUrl", oldTagsApi);
            restore("jimuqu.update.httpProxy", oldProxy);
        }
    }

    private void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
