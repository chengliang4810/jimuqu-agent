package com.jimuqu.solon.claw.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeStatusService;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证 ProfileManager 网关门面在职责下沉后保持端口和状态文件语义。 */
class ProfileGatewayLifecycleServiceTest {
    /** 每个测试独占的临时目录。 */
    @TempDir Path tempDir;

    /** 默认 Profile 根目录。 */
    private Path root;

    /** 被测 Profile 管理器门面。 */
    private ProfileManager manager;

    /** 为每个测试创建独立工作区和命名 Profile。 */
    @BeforeEach
    void setUp() throws Exception {
        root = tempDir.resolve("workspace");
        Files.createDirectories(root.resolve("profiles/alpha"));
        manager = new ProfileManager(root, tempDir.resolve("bin"), "solonclaw");
    }

    /** 显式端口参数采用最后一次输入，并移除重复写法后持久化。 */
    @Test
    void keepsLastExplicitPortAndRemovesDuplicateArguments() throws Exception {
        int firstPort = freePort();
        int lastPort = distinctFreePort(firstPort);

        List<String> arguments =
                manager.gatewayServerArguments(
                        "ALPHA",
                        Arrays.asList(
                                "--server.port",
                                String.valueOf(firstPort),
                                "--server.host=127.0.0.1",
                                "--server.port=" + lastPort));

        assertThat(arguments)
                .containsExactly("--server.host=127.0.0.1", "--server.port=" + lastPort);
        assertThat(manager.gatewayStatus("alpha").getPort()).isEqualTo(lastPort);
        assertThat(root.resolve("profiles/alpha/.profile.json"))
                .content(StandardCharsets.UTF_8)
                .contains("\"gateway_port\":" + lastPort, "\"gateway_port_auto\":false");
    }

    /** 停止未运行网关时清理陈旧状态，并保持状态视图的隔离路径。 */
    @Test
    void clearsStaleFilesAndKeepsGatewayStatusPaths() throws Exception {
        Path home = root.resolve("profiles/alpha");
        write(home.resolve("gateway.pid"), "{}");
        write(home.resolve("gateway_state.json"), "{}");

        manager.stopGateway("ALPHA");

        ProfileGatewayStatus status = manager.gatewayStatus("alpha");
        assertThat(home.resolve("gateway.pid")).doesNotExist();
        assertThat(home.resolve("gateway_state.json")).doesNotExist();
        assertThat(status.getProfile()).isEqualTo("alpha");
        assertThat(status.getHome()).isEqualTo(home);
        assertThat(status.isRunning()).isFalse();
        assertThat(status.getPid()).isNull();
        assertThat(status.getPidFile()).isEqualTo(home.resolve("gateway.pid"));
        assertThat(status.getStateFile()).isEqualTo(home.resolve("gateway_state.json"));
        assertThat(status.getLogFile()).isEqualTo(home.resolve("logs/gateway.log"));
    }

    /** 匹配当前 JVM 的有效 PID 记录仍必须被停止边界拒绝。 */
    @Test
    void refusesToStopCurrentJvmPid() {
        Path home = root.resolve("profiles/alpha");
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(home.toString());
        new GatewayRuntimeStatusService(config, "alpha").writePidFile();

        assertThatThrownBy(() -> manager.stopGateway("alpha"))
                .isInstanceOf(IOException.class)
                .hasMessage("Refusing to stop an invalid profile gateway PID.");
        assertThat(home.resolve("gateway.pid")).exists();
    }

    /** 职责下沉不改变既有构造器和四个公开网关方法的签名与可见性。 */
    @Test
    void keepsProfileManagerGatewayFacadeContract() throws Exception {
        assertThat(
                        Modifier.isPublic(
                                ProfileManager.class
                                        .getDeclaredConstructor(
                                                Path.class, Path.class, String.class)
                                        .getModifiers()))
                .isTrue();
        assertThat(
                        ProfileManager.class
                                .getDeclaredConstructor(
                                        Path.class,
                                        Path.class,
                                        String.class,
                                        ProfileDescriptionService.class)
                                .getModifiers())
                .isZero();
        assertThat(
                        ProfileManager.class
                                .getDeclaredConstructor(
                                        Path.class,
                                        Path.class,
                                        String.class,
                                        ProfileDescriptionService.class,
                                        ProfileBundledSkillSeeder.class)
                                .getModifiers())
                .isZero();
        assertThat(
                        Modifier.isPublic(
                                ProfileManager.class
                                        .getDeclaredMethod("gatewayStatus", String.class)
                                        .getModifiers()))
                .isTrue();
        assertThat(
                        ProfileManager.class
                                .getDeclaredMethod("gatewayStatus", String.class)
                                .getReturnType())
                .isEqualTo(ProfileGatewayStatus.class);
        assertThat(
                        Modifier.isPublic(
                                ProfileManager.class
                                        .getDeclaredMethod(
                                                "gatewayServerArguments", String.class, List.class)
                                        .getModifiers()))
                .isTrue();
        assertThat(
                        ProfileManager.class
                                .getDeclaredMethod(
                                        "gatewayServerArguments", String.class, List.class)
                                .getReturnType())
                .isEqualTo(List.class);
        assertThat(
                        Modifier.isPublic(
                                ProfileManager.class
                                        .getDeclaredMethod("startGateway", String.class, List.class)
                                        .getModifiers()))
                .isTrue();
        assertThat(
                        ProfileManager.class
                                .getDeclaredMethod("startGateway", String.class, List.class)
                                .getReturnType())
                .isEqualTo(Void.TYPE);
        assertThat(
                        Modifier.isPublic(
                                ProfileManager.class
                                        .getDeclaredMethod("stopGateway", String.class)
                                        .getModifiers()))
                .isTrue();
        assertThat(
                        ProfileManager.class
                                .getDeclaredMethod("stopGateway", String.class)
                                .getReturnType())
                .isEqualTo(Void.TYPE);
    }

    /** 获取一个当前可绑定的本机端口。 */
    private int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 获取一个与指定端口不同的当前可绑定端口。 */
    private int distinctFreePort(int excluded) throws Exception {
        int port = freePort();
        while (port == excluded) {
            port = freePort();
        }
        return port;
    }

    /** 创建 UTF-8 测试文件及其父目录。 */
    private void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
