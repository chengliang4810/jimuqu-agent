package com.jimuqu.agent.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.support.constants.RuntimePathConstants;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行时环境变量解析器，统一处理 runtime/.env 与进程环境变量的优先级。
 */
public class RuntimeEnvResolver {
    private static final Object LOCK = new Object();
    private static volatile RuntimeEnvResolver current;

    private final File envFile;
    private volatile long lastLoadedAt;
    private volatile Map<String, String> fileValues = Collections.emptyMap();

    private RuntimeEnvResolver(File envFile) {
        this.envFile = envFile;
        reload();
    }

    /**
     * 基于 runtime.home 初始化全局解析器。
     */
    public static RuntimeEnvResolver initialize(String runtimeHome) {
        File homeDir = resolveRuntimeHome(runtimeHome);
        File envFile = FileUtil.file(homeDir, ".env");
        synchronized (LOCK) {
            if (current == null || !current.envFile.equals(envFile)) {
                current = new RuntimeEnvResolver(envFile);
            } else {
                current.reloadIfNeeded();
            }
            return current;
        }
    }

    /**
     * 返回当前解析器；若尚未初始化，则使用默认 runtime 目录。
     */
    public static RuntimeEnvResolver getInstance() {
        RuntimeEnvResolver instance = current;
        if (instance == null) {
            instance = initialize(RuntimePathConstants.RUNTIME_HOME);
        } else {
            instance.reloadIfNeeded();
        }
        return instance;
    }

    /**
     * 读取生效环境值。OS 环境变量优先于 runtime/.env。
     */
    public static String getenv(String key) {
        return getInstance().get(key);
    }

    /**
     * 返回 runtime/.env 文件路径。
     */
    public File envFile() {
        return envFile;
    }

    /**
     * 读取指定键的生效值。
     */
    public String get(String key) {
        String processValue = System.getenv(key);
        if (StrUtil.isNotBlank(processValue)) {
            return processValue.trim();
        }
        reloadIfNeeded();
        String fileValue = fileValues.get(key);
        return fileValue == null ? null : fileValue.trim();
    }

    /**
     * 返回 runtime/.env 中的文件值快照。
     */
    public Map<String, String> fileValues() {
        reloadIfNeeded();
        return new LinkedHashMap<String, String>(fileValues);
    }

    /**
     * 返回生效值快照，文件值会被同名 OS 环境变量覆盖。
     */
    public Map<String, String> effectiveValues(Iterable<String> keys) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (String key : keys) {
            result.put(key, get(key));
        }
        return result;
    }

    /**
     * 设置 runtime/.env 中的键值。
     */
    public synchronized void setFileValue(String key, String value) {
        Map<String, String> values = fileValues();
        values.put(key, StrUtil.nullToEmpty(value));
        write(values);
    }

    /**
     * 删除 runtime/.env 中的键值。
     */
    public synchronized void removeFileValue(String key) {
        Map<String, String> values = fileValues();
        values.remove(key);
        write(values);
    }

    /**
     * 强制重载 runtime/.env。
     */
    public synchronized void reload() {
        FileUtil.mkParentDirs(envFile);
        if (!envFile.exists()) {
            fileValues = Collections.emptyMap();
            lastLoadedAt = 0L;
            return;
        }

        Map<String, String> values = new LinkedHashMap<String, String>();
        for (String line : FileUtil.readUtf8Lines(envFile)) {
            String trimmed = StrUtil.trim(line);
            if (StrUtil.isBlank(trimmed) || trimmed.startsWith("#")) {
                continue;
            }

            int index = trimmed.indexOf('=');
            if (index <= 0) {
                continue;
            }

            String key = trimmed.substring(0, index).trim();
            String value = trimmed.substring(index + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }

        fileValues = values;
        lastLoadedAt = envFile.lastModified();
    }

    private void reloadIfNeeded() {
        if (!envFile.exists()) {
            if (!fileValues.isEmpty()) {
                reload();
            }
            return;
        }
        if (envFile.lastModified() != lastLoadedAt) {
            synchronized (this) {
                if (envFile.lastModified() != lastLoadedAt) {
                    reload();
                }
            }
        }
    }

    private void write(Map<String, String> values) {
        StringBuilder buffer = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            buffer.append(entry.getKey()).append('=').append(quote(entry.getValue())).append('\n');
        }
        FileUtil.mkParentDirs(envFile);
        FileUtil.writeUtf8String(buffer.toString(), envFile);
        reload();
    }

    private String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(" ") || value.contains("#") || value.contains("=")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private static File resolveRuntimeHome(String runtimeHome) {
        String raw = StrUtil.blankToDefault(runtimeHome, RuntimePathConstants.RUNTIME_HOME);
        File file = new File(raw);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(System.getProperty("user.dir"), raw);
    }
}
