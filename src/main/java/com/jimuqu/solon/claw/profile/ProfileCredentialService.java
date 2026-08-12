package com.jimuqu.solon.claw.profile;

import cn.hutool.core.util.StrUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 管理当前 Profile 的 .env 凭据，并以进程环境变量作为只读回退来源。 */
public final class ProfileCredentialService {
    /** 合法环境变量名。 */
    private static final Pattern ENV_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** 当前 Profile 的工作目录。 */
    private final Path home;

    /** 创建当前 Profile 的凭据管理服务。 */
    public ProfileCredentialService(Path home) {
        if (home == null) {
            throw new IllegalArgumentException("Profile home is required.");
        }
        this.home = home.toAbsolutePath().normalize();
    }

    /** 设置或覆盖 Profile .env 中的凭据，永不返回凭据值。 */
    public synchronized void set(String name, String value) throws IOException {
        validateName(name);
        if (value == null) {
            throw new IllegalArgumentException("Credential value is required.");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>(load());
        values.put(name, value);
        write(values);
        ProfileRuntimeScope.refreshCurrentEnvironment(values);
    }

    /** 从 Profile .env 删除凭据；进程环境中的同名值不受影响。 */
    public synchronized boolean remove(String name) throws IOException {
        validateName(name);
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>(load());
        boolean removed = values.containsKey(name);
        values.remove(name);
        if (removed) {
            write(values);
            ProfileRuntimeScope.refreshCurrentEnvironment(values);
        }
        return removed;
    }

    /** 列出可见凭据的名称与来源，不返回任何值。 */
    public List<CredentialView> list() {
        Map<String, String> profile = load();
        LinkedHashSet<String> names = new LinkedHashSet<String>(profile.keySet());
        if (allowsProcessEnvironmentFallback()) {
            names.addAll(System.getenv().keySet());
        }
        List<CredentialView> views = new ArrayList<CredentialView>();
        for (String name : names) {
            CredentialView view = probe(name);
            if (view.isPresent()) {
                views.add(view);
            }
        }
        return views;
    }

    /** 探测凭据来源，Profile .env 优先且显式空值也会遮蔽进程环境。 */
    public CredentialView probe(String name) {
        validateName(name);
        Map<String, String> profile = load();
        if (profile.containsKey(name)) {
            return new CredentialView(name, "profile_env", true);
        }
        boolean processPresent =
                allowsProcessEnvironmentFallback() && System.getenv().containsKey(name);
        return new CredentialView(name, processPresent ? "process_env" : "missing", processPresent);
    }

    /** 读取当前 Profile .env 快照。 */
    private Map<String, String> load() {
        return ProfileEnvironmentLoader.load(home);
    }

    /** 默认 Profile 回退进程环境，命名 Profile 保持隔离并 fail closed。 */
    private boolean allowsProcessEnvironmentFallback() {
        ProfileRuntimeScope.Context current = ProfileRuntimeScope.current();
        return current == null || "default".equalsIgnoreCase(current.getProfile());
    }

    /** 原子写入 .env，并在 POSIX 文件系统上强制权限为 600。 */
    private void write(Map<String, String> values) throws IOException {
        Files.createDirectories(home);
        Path target = home.resolve(".env");
        Path temporary = Files.createTempFile(home, ".env.", ".tmp");
        try {
            List<String> lines = new ArrayList<String>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                lines.add(entry.getKey() + "=" + quote(entry.getValue()));
            }
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            setOwnerOnlyPermissions(temporary);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setOwnerOnlyPermissions(target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 把任意文本安全编码为 dotenv 双引号值。 */
    private String quote(String value) {
        return "\""
                + StrUtil.nullToEmpty(value)
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                + "\"";
    }

    /** 在支持 POSIX 权限的平台上只允许文件所有者读写。 */
    private void setOwnerOnlyPermissions(Path file) throws IOException {
        try {
            Set<PosixFilePermission> permissions =
                    new LinkedHashSet<PosixFilePermission>(
                            Arrays.asList(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE));
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException e) {
            // Windows ACL 不支持 POSIX mode；由运行账户的目录 ACL 负责隔离。
        }
    }

    /** 校验环境变量名。 */
    private void validateName(String name) {
        if (name == null || !ENV_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid credential environment variable name.");
        }
    }

    /** 不含凭据值的来源视图。 */
    public static final class CredentialView {
        /** 环境变量名。 */
        private final String name;

        /** 凭据来源。 */
        private final String source;

        /** 是否存在。 */
        private final boolean present;

        /** 创建不含值的凭据来源视图。 */
        public CredentialView(String name, String source, boolean present) {
            this.name = name;
            this.source = source;
            this.present = present;
        }

        /** 返回环境变量名。 */
        public String getName() {
            return name;
        }

        /** 返回 profile_env、process_env 或 missing。 */
        public String getSource() {
            return source;
        }

        /** 返回凭据是否存在。 */
        public boolean isPresent() {
            return present;
        }
    }
}
