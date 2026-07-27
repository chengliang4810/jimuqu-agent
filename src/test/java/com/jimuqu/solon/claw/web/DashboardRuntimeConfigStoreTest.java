package com.jimuqu.solon.claw.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.profile.ProfileManager;
import com.jimuqu.solon.claw.web.profile.DashboardProfileContext;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Dashboard 工作区配置存储、Profile 和刷新矩阵测试。 */
class DashboardRuntimeConfigStoreTest {
    /** 每个测试独占的工作区根目录。 */
    @TempDir Path tempDir;

    /** 当前 Profile 写入和删除必须按参数选择配置刷新或渠道重连。 */
    @Test
    void shouldRefreshCurrentProfileWithRequestedMode() {
        Path home = tempDir.resolve("current");
        AppConfig config = config(home);
        RecordingRefreshService refreshService = new RecordingRefreshService(config);
        DashboardRuntimeConfigStore store =
                new DashboardRuntimeConfigStore(config, refreshService, null);

        store.write("solonclaw.react.maxSteps", "7", false, null);
        store.write("solonclaw.react.maxSteps", "8", true, null);
        assertThat(RuntimeConfigResolver.open(home.toString()).get("solonclaw.react.maxSteps"))
                .isEqualTo("8");
        assertThat(refreshService.configOnlyCalls).isEqualTo(1);
        assertThat(refreshService.reconnectCalls).isEqualTo(1);

        store.remove("solonclaw.react.maxSteps", false, null);
        assertThat(RuntimeConfigResolver.open(home.toString()).get("solonclaw.react.maxSteps"))
                .isNull();
        assertThat(refreshService.configOnlyCalls).isEqualTo(2);
        assertThat(refreshService.reconnectCalls).isEqualTo(1);
    }

    /** 命名 Profile 写入必须隔离文件，并且不能刷新当前 JVM。 */
    @Test
    void shouldIsolateDetachedProfileWithoutRefreshingCurrentRuntime() throws Exception {
        Path root = tempDir.resolve("profiles-root");
        Path workerHome = root.resolve("profiles/worker");
        Files.createDirectories(workerHome);
        AppConfig config = config(root);
        RecordingRefreshService refreshService = new RecordingRefreshService(config);
        ProfileManager profileManager =
                new ProfileManager(root, tempDir.resolve("bin"), "solonclaw");
        DashboardProfileContext profileContext =
                new DashboardProfileContext(profileManager, config);
        DashboardRuntimeConfigStore store =
                new DashboardRuntimeConfigStore(config, refreshService, profileContext);

        store.write("solonclaw.react.maxSteps", "3", false, "current");
        store.write("solonclaw.react.maxSteps", "19", true, "WoRkEr");

        assertThat(RuntimeConfigResolver.open(root.toString()).get("solonclaw.react.maxSteps"))
                .isEqualTo("3");
        assertThat(
                        RuntimeConfigResolver.open(workerHome.toString())
                                .get("solonclaw.react.maxSteps"))
                .isEqualTo("19");
        assertThat(store.resolveProfileName("WoRkEr")).isEqualTo("worker");
        assertThat(refreshService.configOnlyCalls).isEqualTo(1);
        assertThat(refreshService.reconnectCalls).isZero();

        store.remove("solonclaw.react.maxSteps", true, "worker");
        assertThat(
                        RuntimeConfigResolver.open(workerHome.toString())
                                .get("solonclaw.react.maxSteps"))
                .isNull();
        assertThat(refreshService.configOnlyCalls).isEqualTo(1);
        assertThat(refreshService.reconnectCalls).isZero();
    }

    /**
     * 创建绑定指定工作区的测试配置。
     *
     * @param home 工作区根目录。
     * @return 测试配置。
     */
    private AppConfig config(Path home) {
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(home.toAbsolutePath().normalize().toString());
        config.getRuntime().setConfigFile(home.resolve("config.yml").toString());
        config.getWorkspace().setDir(home.toAbsolutePath().normalize().toString());
        return config;
    }

    /** 记录 Store 选择的刷新模式，避免测试启动真实渠道。 */
    private static final class RecordingRefreshService extends GatewayRuntimeRefreshService {
        /** 只刷新配置的调用次数。 */
        private int configOnlyCalls;

        /** 刷新并重连渠道的调用次数。 */
        private int reconnectCalls;

        /**
         * 创建刷新调用记录器。
         *
         * @param config 当前 JVM 配置。
         */
        private RecordingRefreshService(AppConfig config) {
            super(config, null);
        }

        /**
         * 记录配置刷新调用。
         *
         * @return 不执行真实刷新的空结果。
         */
        @Override
        public RefreshResult refreshConfigOnly() {
            configOnlyCalls++;
            return null;
        }

        /**
         * 记录渠道重连调用。
         *
         * @return 不执行真实刷新的空结果。
         */
        @Override
        public RefreshResult refreshNow() {
            reconnectCalls++;
            return null;
        }
    }
}
