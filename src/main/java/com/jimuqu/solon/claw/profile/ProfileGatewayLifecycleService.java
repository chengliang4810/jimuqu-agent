package com.jimuqu.solon.claw.profile;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeStatusService;
import com.jimuqu.solon.claw.support.RuntimeProcessSupport;
import com.jimuqu.solon.claw.support.SecretRedactor;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.noear.snack4.ONode;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** 管理 Profile 网关的状态、端口分配和后台进程生命周期。 */
final class ProfileGatewayLifecycleService {
    /** 每个 Profile 后台网关的合并日志相对路径。 */
    private static final String GATEWAY_LOG_FILE = "logs/gateway.log";

    /** 所有 Profile 共用的网关启动锁，避免跨进程端口分配与启动竞争。 */
    private static final String GATEWAY_START_LOCK_FILE = "profiles/.gateway-start.lock";

    /** 后台网关启动等待上限。 */
    private static final long GATEWAY_START_TIMEOUT_MILLIS = 30000L;

    /** Profile 本机元数据文件名。 */
    private static final String METADATA_FILE = ".profile.json";

    /** 提供 Profile 路径、名称和本机元数据访问能力的管理器。 */
    private final ProfileManager profileManager;

    /** 默认 Profile 的规范化根目录。 */
    private final Path root;

    /**
     * 创建 Profile 网关生命周期服务。
     *
     * @param profileManager Profile 管理器。
     * @param root 默认 Profile 根目录。
     */
    ProfileGatewayLifecycleService(ProfileManager profileManager, Path root) {
        if (profileManager == null || root == null) {
            throw new IllegalArgumentException("Profile manager and root are required.");
        }
        this.profileManager = profileManager;
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * 返回指定 Profile 的网关运行状态和隔离文件路径。
     *
     * @param rawName Profile 名。
     * @return 网关状态视图。
     * @throws Exception Profile 不存在或状态文件无法读取。
     */
    ProfileGatewayStatus status(String rawName) throws Exception {
        Path home = profileManager.requireProfileHome(rawName);
        String name = profileName(home);
        GatewayRuntimeStatusService service = statusService(name, home);
        boolean running = service.isRunning();
        Long pid = null;
        Integer port = knownGatewayPort(name, home);
        if (running) {
            Map<String, Object> record = readJson(home.resolve("gateway.pid"), true);
            long value = longValue(record.get("pid"));
            if (value > 0L) {
                pid = Long.valueOf(value);
            }
            int recordedPort = intValue(record.get("port"), -1);
            if (recordedPort > 0) {
                port = Integer.valueOf(recordedPort);
            }
        }
        return new ProfileGatewayStatus(
                name,
                home,
                running,
                pid,
                port,
                service.readState(),
                home.resolve("gateway.pid"),
                home.resolve("gateway_state.json"),
                home.resolve(GATEWAY_LOG_FILE));
    }

    /**
     * 为前台或后台网关生成唯一端口参数。
     *
     * @param rawName Profile 名。
     * @param rawArgs 原始服务端参数。
     * @return 去重后带监听端口的参数副本。
     * @throws Exception Profile 不存在、端口无效或无可用端口。
     */
    List<String> serverArguments(String rawName, List<String> rawArgs) throws Exception {
        synchronized (ProfileManager.class) {
            Path lockFile = root.resolve(GATEWAY_START_LOCK_FILE);
            Files.createDirectories(lockFile.getParent());
            try (FileChannel channel =
                            FileChannel.open(
                                    lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                return serverArgumentsLocked(rawName, rawArgs);
            }
        }
    }

    /**
     * 为指定 Profile 启动独立后台 JVM。
     *
     * @param rawName Profile 名。
     * @param serverArgs 传给 Solon 服务端的附加参数。
     * @throws Exception 启动命令不可解析、子进程退出或超时。
     */
    void start(String rawName, List<String> serverArgs) throws Exception {
        synchronized (ProfileManager.class) {
            Path lockFile = root.resolve(GATEWAY_START_LOCK_FILE);
            Files.createDirectories(lockFile.getParent());
            try (FileChannel channel =
                            FileChannel.open(
                                    lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                startLocked(rawName, serverArgs);
            }
        }
    }

    /**
     * 停止指定 Profile 的独立网关进程。
     *
     * @param rawName Profile 名。
     * @throws Exception Profile 不存在、PID 不安全或进程无法停止。
     */
    void stop(String rawName) throws Exception {
        stop(profileManager.requireProfileHome(rawName));
    }

    /**
     * 停止指定工作区内确认为 Profile 网关的进程。
     *
     * @param home Profile 工作区。
     * @throws Exception PID 不安全或进程无法停止。
     */
    void stop(Path home) throws Exception {
        String name = profileName(home);
        GatewayRuntimeStatusService statusService = statusService(name, home);
        if (!statusService.isRunning()) {
            Files.deleteIfExists(home.resolve("gateway.pid"));
            Files.deleteIfExists(home.resolve("gateway_state.json"));
            return;
        }
        Map<String, Object> record = readJson(home.resolve("gateway.pid"), true);
        long pid = longValue(record.get("pid"));
        if (pid <= 0L || pid == RuntimeProcessSupport.currentPidOrUnknown()) {
            throw new IOException("Refusing to stop an invalid profile gateway PID.");
        }
        terminateProcess(pid, false);
        for (int i = 0; i < 30 && statusService.isRunning(); i++) {
            Thread.sleep(100L);
        }
        if (statusService.isRunning()) {
            terminateProcess(pid, true);
            for (int i = 0; i < 20 && statusService.isRunning(); i++) {
                Thread.sleep(100L);
            }
        }
        if (statusService.isRunning()) {
            throw new IOException("Profile gateway did not stop: PID " + pid);
        }
        Files.deleteIfExists(home.resolve("gateway.pid"));
        Files.deleteIfExists(home.resolve("gateway_state.json"));
    }

    /** 在网关启动锁内完成端口选择和元数据持久化。 */
    private List<String> serverArgumentsLocked(String rawName, List<String> rawArgs)
            throws Exception {
        Path home = profileManager.requireProfileHome(rawName);
        String name = profileName(home);
        List<String> source = rawArgs == null ? Collections.<String>emptyList() : rawArgs;
        List<String> result = new ArrayList<String>();
        Integer explicitPort = null;
        for (int i = 0; i < source.size(); i++) {
            String argument = source.get(i);
            if ("--server.port".equals(argument)) {
                if (i + 1 >= source.size()) {
                    throw new IllegalArgumentException("--server.port requires a value.");
                }
                explicitPort = Integer.valueOf(parsePort(source.get(++i)));
                continue;
            }
            if (argument != null && argument.startsWith("--server.port=")) {
                explicitPort =
                        Integer.valueOf(parsePort(argument.substring("--server.port=".length())));
                continue;
            }
            result.add(argument);
        }
        PortSelection selection = selectGatewayPort(name, home, explicitPort);
        result.add("--server.port=" + selection.port);
        if (!"default".equals(name)) {
            Map<String, Object> metadata = readMetadata(home);
            metadata.put("name", name);
            if (!metadata.containsKey("aliases")) {
                metadata.put("aliases", new ArrayList<String>());
            }
            metadata.put("gateway_port", Integer.valueOf(selection.port));
            metadata.put("gateway_port_auto", Boolean.valueOf(selection.automatic));
            writeMetadata(home, metadata);
        }
        return result;
    }

    /** 在工作区级启动锁内复核状态并等待独立网关完成启动。 */
    private void startLocked(String rawName, List<String> serverArgs) throws Exception {
        Path home = profileManager.requireProfileHome(rawName);
        String name = profileName(home);
        if (running(name, home)) {
            return;
        }
        Files.deleteIfExists(home.resolve("gateway.pid"));
        Files.deleteIfExists(home.resolve("gateway_state.json"));
        Path logFile = home.resolve(GATEWAY_LOG_FILE);
        Files.createDirectories(logFile.getParent());
        List<String> effectiveArgs = serverArgumentsLocked(name, serverArgs);
        List<String> command = launchCommand(name, home, effectiveArgs);
        ProcessBuilder builder = new ProcessBuilder(command);
        configureProcessEnvironment(builder, name, home);
        builder.directory(new File(System.getProperty("user.dir", ".")));
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        File nullDevice = new File(isWindows() ? "NUL" : "/dev/null");
        if (nullDevice.exists()) {
            builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice));
        }
        Process process = builder.start();
        long deadline = System.currentTimeMillis() + GATEWAY_START_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (running(name, home)) {
                return;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(100L);
        }
        if (process.isAlive()) {
            process.destroy();
            process.waitFor(3L, TimeUnit.SECONDS);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        Files.deleteIfExists(home.resolve("gateway.pid"));
        Files.deleteIfExists(home.resolve("gateway_state.json"));
        String detail = readLogTail(logFile, 4000);
        throw new IOException(
                "Profile gateway failed to start"
                        + (detail.length() == 0
                                ? "."
                                : ": " + SecretRedactor.redact(detail, 4000)));
    }

    /** 构建与当前 jar 或类路径一致的后台服务端启动命令。 */
    private List<String> launchCommand(String profile, Path home, List<String> serverArgs)
            throws Exception {
        List<String> command = new ArrayList<String>();
        Path java =
                Paths.get(
                                System.getProperty("java.home", ""),
                                "bin",
                                isWindows() ? "java.exe" : "java")
                        .toAbsolutePath()
                        .normalize();
        if (!Files.isRegularFile(java)) {
            throw new IOException("Java runtime executable was not found: " + java);
        }
        command.add(java.toString());
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dsolonclaw.profile.root=" + root);
        command.add("-Dsolonclaw.workspace=" + home);
        command.add("-Dsolonclaw.profile.name=" + profile);
        URI location =
                ProfileManager.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path codeSource = Paths.get(location).toAbsolutePath().normalize();
        if (Files.isRegularFile(codeSource)
                && codeSource.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            command.add("-jar");
            command.add(codeSource.toString());
        } else {
            String classPath = trimToNull(System.getProperty("java.class.path"));
            if (classPath == null) {
                throw new IOException("Current Java classpath is unavailable for gateway start.");
            }
            command.add("-cp");
            command.add(classPath);
            command.add("com.jimuqu.solon.claw.SolonClawApp");
        }
        command.add("--profile");
        command.add(profile);
        if (serverArgs != null) {
            for (String argument : serverArgs) {
                if (argument != null && argument.indexOf('\0') < 0) {
                    command.add(argument);
                }
            }
        }
        return command;
    }

    /**
     * 用命名 Profile 的安全环境快照替换后台网关进程环境。
     *
     * <p>default 网关继续继承当前进程环境；命名 Profile 只继承运行必需的系统变量，并叠加自己的 .env，避免父进程中的其他 Profile 凭据泄露到子进程。
     *
     * @param builder 待启动的后台网关进程。
     * @param profile 规范化 Profile 名。
     * @param home Profile 工作区。
     */
    void configureProcessEnvironment(ProcessBuilder builder, String profile, Path home) {
        if ("default".equals(profile)) {
            return;
        }
        Map<String, String> environment = ProfileEnvironmentLoader.load(home);
        try (ProfileRuntimeScope.Scope ignored =
                ProfileRuntimeScope.open(profile, home, environment, null)) {
            ProfileRuntimeScope.replaceProcessEnvironment(builder.environment());
        }
    }

    /** 选择显式、已持久化、配置或自动分配的 Profile 网关端口。 */
    private PortSelection selectGatewayPort(String name, Path home, Integer explicitPort)
            throws Exception {
        if (explicitPort != null) {
            requireAvailableGatewayPort(name, explicitPort.intValue());
            return new PortSelection(explicitPort.intValue(), false);
        }
        Map<String, Object> metadata = readMetadata(home);
        int metadataPort = intValue(metadata.get("gateway_port"), -1);
        boolean automatic = Boolean.TRUE.equals(metadata.get("gateway_port_auto"));
        if (metadataPort > 0) {
            if (isGatewayPortAvailable(metadataPort)) {
                return new PortSelection(metadataPort, automatic);
            }
            if (!automatic) {
                throw new IOException(
                        "Gateway port "
                                + metadataPort
                                + " for profile '"
                                + name
                                + "' is already in use.");
            }
        }
        Integer configured = readConfiguredGatewayPort(home.resolve("config.yml"));
        if ("default".equals(name)) {
            int port = configured == null ? 8080 : configured.intValue();
            requireAvailableGatewayPort(name, port);
            return new PortSelection(port, false);
        }
        if (configured != null && configured.intValue() != 8080) {
            requireAvailableGatewayPort(name, configured.intValue());
            return new PortSelection(configured.intValue(), false);
        }
        return new PortSelection(findAvailableGatewayPort(name), true);
    }

    /** 返回已知监听端口但不分配或写入新的端口。 */
    private Integer knownGatewayPort(String name, Path home) throws IOException {
        Map<String, Object> metadata = readMetadata(home);
        int metadataPort = intValue(metadata.get("gateway_port"), -1);
        if (metadataPort > 0) {
            return Integer.valueOf(metadataPort);
        }
        Integer configured = readConfiguredGatewayPort(home.resolve("config.yml"));
        if (configured != null && ("default".equals(name) || configured.intValue() != 8080)) {
            return configured;
        }
        return "default".equals(name) ? Integer.valueOf(8080) : null;
    }

    /** 从 Profile 配置文件读取显式 server.port。 */
    private Integer readConfiguredGatewayPort(Path config) throws IOException {
        if (!Files.isRegularFile(config)) {
            return null;
        }
        try {
            Object parsed =
                    new Yaml(new SafeConstructor(new LoaderOptions())).load(readText(config));
            if (!(parsed instanceof Map)) {
                return null;
            }
            Map<String, Object> rootMap = stringMap((Map<?, ?>) parsed);
            Object raw = rootMap.get("server.port");
            if (raw == null) {
                raw = mapValue(rootMap.get("server")).get("port");
            }
            if (raw == null) {
                return null;
            }
            return Integer.valueOf(parsePort(String.valueOf(raw)));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Invalid server.port in " + config, e);
        }
    }

    /** 为命名 Profile 查找未被现有 Profile 预留且当前可绑定的端口。 */
    private int findAvailableGatewayPort(String profile) throws Exception {
        Set<Integer> reserved = reservedGatewayPorts(profile);
        int first = 8081 + Math.floorMod(profile.hashCode(), 1000);
        for (int offset = 0; offset < 10000; offset++) {
            int candidate = 8081 + Math.floorMod(first - 8081 + offset, 10000);
            if (!reserved.contains(Integer.valueOf(candidate))
                    && isGatewayPortAvailable(candidate)) {
                return candidate;
            }
        }
        throw new IOException(
                "No available gateway port could be allocated for profile '" + profile + "'.");
    }

    /** 收集其他 Profile 已持久化或显式配置的端口。 */
    private Set<Integer> reservedGatewayPorts(String exceptProfile) throws Exception {
        Set<Integer> result = new HashSet<Integer>();
        result.add(Integer.valueOf(8080));
        for (String name : profileManager.listProfileNames()) {
            if (name.equals(exceptProfile)) {
                continue;
            }
            Path home = profileManager.requireProfileHome(name);
            Integer port = knownGatewayPort(name, home);
            if (port != null) {
                result.add(port);
            }
        }
        return result;
    }

    /** 验证端口范围和当前绑定可用性。 */
    private void requireAvailableGatewayPort(String profile, int port) throws IOException {
        validatePort(port);
        if (!isGatewayPortAvailable(port)) {
            throw new IOException(
                    "Gateway port " + port + " for profile '" + profile + "' is already in use.");
        }
    }

    /** 尝试在回环地址绑定端口，用于启动前快速冲突检查。 */
    private boolean isGatewayPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            socket.setReuseAddress(false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析并校验 TCP 监听端口。 */
    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value == null ? "" : value.trim());
            validatePort(port);
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid server.port: " + value);
        }
    }

    /** 校验 TCP 监听端口范围。 */
    private void validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid server.port: " + port);
        }
    }

    /** 使用现有网关状态服务读取 Profile 本地 PID/状态。 */
    private boolean running(String name, Path home) {
        return statusService(name, home).isRunning();
    }

    /** 创建绑定指定 Profile 工作区的网关状态服务。 */
    private GatewayRuntimeStatusService statusService(String name, Path home) {
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(home.toString());
        return new GatewayRuntimeStatusService(config, name);
    }

    /** 根据工作区解析规范 Profile 名。 */
    private String profileName(Path home) {
        Path normalized = home.toAbsolutePath().normalize();
        return normalized.equals(root) ? "default" : normalized.getFileName().toString();
    }

    /** 读取后台网关日志尾部，避免错误输出加载整个大文件。 */
    private String readLogTail(Path logFile, int limit) {
        if (!Files.isRegularFile(logFile) || limit <= 0) {
            return "";
        }
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = file.length();
            int count = (int) Math.min((long) limit, length);
            byte[] bytes = new byte[count];
            file.seek(length - count);
            file.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** 向已验证的网关 PID 发送终止信号。 */
    private void terminateProcess(long pid, boolean force)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<String>();
        if (isWindows()) {
            command.add("taskkill");
            command.add("/PID");
            command.add(String.valueOf(pid));
            command.add("/T");
            if (force) {
                command.add("/F");
            }
        } else {
            command.add("kill");
            command.add(force ? "-KILL" : "-TERM");
            command.add(String.valueOf(pid));
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.waitFor(5L, TimeUnit.SECONDS);
    }

    /** 读取 Profile 本机元数据；不存在时返回空映射。 */
    private Map<String, Object> readMetadata(Path home) throws IOException {
        Path metadata = home.resolve(METADATA_FILE);
        return Files.isRegularFile(metadata)
                ? readJson(metadata, false)
                : new LinkedHashMap<String, Object>();
    }

    /** 使用原子替换语义写回 Profile 本机元数据。 */
    private void writeMetadata(Path home, Map<String, Object> metadata) throws IOException {
        Path path = home.resolve(METADATA_FILE);
        writeAtomically(path, ONode.serialize(metadata) + System.lineSeparator());
    }

    /** 原子写入 UTF-8 文本，文件系统不支持原子移动时回退普通替换。 */
    private void writeAtomically(Path path, String content) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(
                temporary,
                content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 使用 Snack4 读取 JSON 对象。 */
    private Map<String, Object> readJson(Path path, boolean strict) throws IOException {
        try {
            Object parsed = ONode.deserialize(readText(path), Object.class);
            if (parsed instanceof Map) {
                return stringMap((Map<?, ?>) parsed);
            }
        } catch (Exception e) {
            if (strict) {
                throw new IOException("Invalid JSON file: " + path, e);
            }
        }
        if (strict) {
            throw new IOException("JSON file must contain an object: " + path);
        }
        return new LinkedHashMap<String, Object>();
    }

    /** 读取 UTF-8 文本。 */
    private String readText(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 将通用映射键转换为字符串。 */
    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (source != null) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return result;
    }

    /** 将值安全转换为字符串映射。 */
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map
                ? stringMap((Map<?, ?>) value)
                : new LinkedHashMap<String, Object>();
    }

    /** 将任意值转为去空白文本。 */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 将值转换为长整型，失败时返回 -1。 */
    private long longValue(Object value) {
        try {
            return Long.parseLong(text(value));
        } catch (Exception e) {
            return -1L;
        }
    }

    /** 将值转换为整数，失败时返回默认值。 */
    private int intValue(Object value, int fallback) {
        try {
            return Integer.parseInt(text(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 将空白字符串转换为 null。 */
    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }

    /** 判断当前操作系统是否为 Windows。 */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** 保存网关端口及其是否由系统自动分配。 */
    private static final class PortSelection {
        /** 最终监听端口。 */
        private final int port;

        /** 是否由 Profile 管理器自动分配。 */
        private final boolean automatic;

        /** 创建端口选择结果。 */
        private PortSelection(int port, boolean automatic) {
            this.port = port;
            this.automatic = automatic;
        }
    }
}
