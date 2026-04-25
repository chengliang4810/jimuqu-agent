package com.jimuqu.agent.config;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.support.constants.RuntimePathConstants;
import org.noear.snack4.ONode;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时配置解析器，统一处理 runtime/config.yml 中的可写配置项。
 */
public class RuntimeConfigResolver {
    private static final Object LOCK = new Object();
    private static volatile RuntimeConfigResolver current;
    private static final Map<String, String> KEY_PATHS = buildKeyPaths();

    private final File configFile;
    private volatile long lastLoadedAt;
    private volatile Map<String, Object> fileValues = Collections.emptyMap();

    private RuntimeConfigResolver(File configFile) {
        this.configFile = configFile;
        reload();
    }

    /**
     * 基于 runtime.home 初始化全局解析器。
     */
    public static RuntimeConfigResolver initialize(String runtimeHome) {
        File homeDir = resolveRuntimeHome(runtimeHome);
        File configFile = FileUtil.file(homeDir, "config.yml");
        synchronized (LOCK) {
            if (current == null || !current.configFile.equals(configFile)) {
                current = new RuntimeConfigResolver(configFile);
            } else {
                current.reloadIfNeeded();
            }
            return current;
        }
    }

    /**
     * 返回当前解析器；若尚未初始化，则使用默认 runtime 目录。
     */
    public static RuntimeConfigResolver getInstance() {
        RuntimeConfigResolver instance = current;
        if (instance == null) {
            instance = initialize(RuntimePathConstants.RUNTIME_HOME);
        } else {
            instance.reloadIfNeeded();
        }
        return instance;
    }

    /**
     * 读取生效配置值。
     */
    public static String getValue(String key) {
        return getInstance().get(key);
    }

    /**
     * 返回 runtime/config.yml 文件路径。
     */
    public File configFile() {
        return configFile;
    }
    /**
     * 读取指定键的生效值。
     */
    public String get(String key) {
        reloadIfNeeded();
        String path = resolvePath(key);
        if (StrUtil.isBlank(path)) {
            return null;
        }
        return stringify(fileValues.get(path));
    }

    /**
     * 返回 runtime/config.yml 中的文件值快照。
     */
    public Map<String, String> fileValues() {
        reloadIfNeeded();
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : KEY_PATHS.entrySet()) {
            String value = stringify(fileValues.get(entry.getValue()));
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * 返回生效值快照。
     */
    public Map<String, String> effectiveValues(Iterable<String> keys) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (String key : keys) {
            result.put(key, get(key));
        }
        return result;
    }

    /**
     * 设置 runtime/config.yml 中的键值。
     */
    public synchronized void setFileValue(String key, String value) {
        String path = requirePath(key);
        Map<String, Object> root = loadYamlRoot();
        setNestedValue(root, path, StrUtil.nullToEmpty(value));
        write(root);
    }

    /**
     * 删除 runtime/config.yml 中的键值。
     */
    public synchronized void removeFileValue(String key) {
        String path = requirePath(key);
        Map<String, Object> root = loadYamlRoot();
        removeNestedValue(root, path);
        write(root);
    }

    /**
     * 强制重载 runtime/config.yml。
     */
    public synchronized void reload() {
        FileUtil.mkParentDirs(configFile);
        if (!configFile.exists()) {
            fileValues = Collections.emptyMap();
            lastLoadedAt = 0L;
            return;
        }

        try {
            Map<String, Object> flattened = new LinkedHashMap<String, Object>();
            Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
            if (parsed instanceof Map) {
                flatten("", sanitizeMap((Map<?, ?>) parsed), flattened);
            }
            fileValues = flattened;
            lastLoadedAt = configFile.lastModified();
        } catch (Exception e) {
            lastLoadedAt = configFile.lastModified();
        }
    }

    private void reloadIfNeeded() {
        if (!configFile.exists()) {
            if (!fileValues.isEmpty()) {
                reload();
            }
            return;
        }
        if (configFile.lastModified() != lastLoadedAt) {
            synchronized (this) {
                if (configFile.lastModified() != lastLoadedAt) {
                    reload();
                }
            }
        }
    }

    private Map<String, Object> loadYamlRoot() {
        if (!configFile.exists()) {
            return new LinkedHashMap<String, Object>();
        }
        Object parsed = new Yaml().load(FileUtil.readUtf8String(configFile));
        if (!(parsed instanceof Map)) {
            return new LinkedHashMap<String, Object>();
        }
        return sanitizeMap((Map<?, ?>) parsed);
    }

    private void write(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        FileUtil.mkParentDirs(configFile);
        try {
            File temp = new File(configFile.getParentFile(), configFile.getName() + ".tmp");
            FileUtil.writeUtf8String(new Yaml(options).dump(root), temp);
            try {
                Files.move(temp.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(temp.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write runtime config", e);
        }
        reload();
    }

    private String requirePath(String key) {
        String path = resolvePath(key);
        if (StrUtil.isBlank(path)) {
            throw new IllegalStateException("Unsupported config key: " + key);
        }
        return path;
    }

    private String resolvePath(String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        String mapped = KEY_PATHS.get(key);
        if (StrUtil.isNotBlank(mapped)) {
            return mapped;
        }
        if (key.startsWith("jimuqu.")) {
            return key;
        }
        return null;
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            StringBuilder buffer = new StringBuilder();
            for (Object item : (List<?>) value) {
                if (item == null || StrUtil.isBlank(String.valueOf(item))) {
                    continue;
                }
                if (buffer.length() > 0) {
                    buffer.append(',');
                }
                buffer.append(String.valueOf(item).trim());
            }
            return buffer.toString();
        }
        if (value instanceof Map) {
            return ONode.serialize(value);
        }
        return String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object currentValue = cursor.get(parts[i]);
            if (!(currentValue instanceof Map)) {
                currentValue = new LinkedHashMap<String, Object>();
                cursor.put(parts[i], currentValue);
            }
            cursor = (Map<String, Object>) currentValue;
        }
        cursor.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private boolean removeNestedValue(Map<String, Object> root, String path) {
        String[] parts = path.split("\\.");
        List<Map<String, Object>> parents = new ArrayList<Map<String, Object>>();
        List<String> keys = new ArrayList<String>();
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object currentValue = cursor.get(parts[i]);
            if (!(currentValue instanceof Map)) {
                return false;
            }
            parents.add(cursor);
            keys.add(parts[i]);
            cursor = (Map<String, Object>) currentValue;
        }
        Object removed = cursor.remove(parts[parts.length - 1]);
        if (removed == null) {
            return false;
        }
        for (int i = parents.size() - 1; i >= 0; i--) {
            Object currentValue = parents.get(i).get(keys.get(i));
            if (currentValue instanceof Map && ((Map<?, ?>) currentValue).isEmpty()) {
                parents.get(i).remove(keys.get(i));
            } else {
                break;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = sanitizeMap((Map<?, ?>) value);
            } else if (value instanceof List) {
                value = sanitizeList((List<?>) value);
            }
            result.put(String.valueOf(entry.getKey()), value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> sanitizeList(List<?> input) {
        List<Object> result = new ArrayList<Object>();
        for (Object item : input) {
            if (item instanceof Map) {
                result.add(sanitizeMap((Map<?, ?>) item));
            } else if (item instanceof List) {
                result.add(sanitizeList((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private static void flatten(String prefix, Map<?, ?> input, Map<String, Object> output) {
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = prefix.length() == 0 ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<?, ?>) value, output);
            } else {
                output.put(key, value);
            }
        }
    }

    private static File resolveRuntimeHome(String runtimeHome) {
        String raw = StrUtil.blankToDefault(runtimeHome, RuntimePathConstants.RUNTIME_HOME);
        File file = new File(raw);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(System.getProperty("user.dir"), raw);
    }

    private static Map<String, String> buildKeyPaths() {
        Map<String, String> mappings = new LinkedHashMap<String, String>();
        add(mappings, "JIMUQU_RUNTIME_HOME", "jimuqu.runtime.home");
        add(mappings, "JIMUQU_RUNTIME_CONTEXT_DIR", "jimuqu.runtime.contextDir");
        add(mappings, "JIMUQU_RUNTIME_SKILLS_DIR", "jimuqu.runtime.skillsDir");
        add(mappings, "JIMUQU_RUNTIME_CACHE_DIR", "jimuqu.runtime.cacheDir");
        add(mappings, "JIMUQU_RUNTIME_STATE_DB", "jimuqu.runtime.stateDb");

        add(mappings, "JIMUQU_LLM_PROVIDER_KEY", "model.providerKey");
        add(mappings, "JIMUQU_LLM_DEFAULT_MODEL", "model.default");
        add(mappings, "JIMUQU_DEFAULT_PROVIDER_NAME", "providers.default.name");
        add(mappings, "JIMUQU_DEFAULT_PROVIDER_BASE_URL", "providers.default.baseUrl");
        add(mappings, "JIMUQU_DEFAULT_PROVIDER_API_KEY", "providers.default.apiKey");
        add(mappings, "JIMUQU_DEFAULT_PROVIDER_MODEL", "providers.default.defaultModel");
        add(mappings, "JIMUQU_DEFAULT_PROVIDER_DIALECT", "providers.default.dialect");
        add(mappings, "JIMUQU_LLM_STREAM", "jimuqu.llm.stream");
        add(mappings, "JIMUQU_LLM_REASONING_EFFORT", "jimuqu.llm.reasoningEffort");
        add(mappings, "JIMUQU_LLM_TEMPERATURE", "jimuqu.llm.temperature");
        add(mappings, "JIMUQU_LLM_MAX_TOKENS", "jimuqu.llm.maxTokens");
        add(mappings, "JIMUQU_LLM_CONTEXT_WINDOW_TOKENS", "jimuqu.llm.contextWindowTokens");

        add(mappings, "JIMUQU_SCHEDULER_ENABLED", "jimuqu.scheduler.enabled");
        add(mappings, "JIMUQU_SCHEDULER_TICK_SECONDS", "jimuqu.scheduler.tickSeconds");

        add(mappings, "JIMUQU_COMPRESSION_ENABLED", "jimuqu.compression.enabled");
        add(mappings, "JIMUQU_COMPRESSION_THRESHOLD_PERCENT", "jimuqu.compression.thresholdPercent");
        add(mappings, "JIMUQU_COMPRESSION_SUMMARY_MODEL", "jimuqu.compression.summaryModel");
        add(mappings, "JIMUQU_COMPRESSION_PROTECT_HEAD_MESSAGES", "jimuqu.compression.protectHeadMessages");
        add(mappings, "JIMUQU_COMPRESSION_TAIL_RATIO", "jimuqu.compression.tailRatio");

        add(mappings, "JIMUQU_LEARNING_ENABLED", "jimuqu.learning.enabled");
        add(mappings, "JIMUQU_LEARNING_TOOL_CALL_THRESHOLD", "jimuqu.learning.toolCallThreshold");

        add(mappings, "JIMUQU_ROLLBACK_ENABLED", "jimuqu.rollback.enabled");
        add(mappings, "JIMUQU_ROLLBACK_MAX_CHECKPOINTS_PER_SOURCE", "jimuqu.rollback.maxCheckpointsPerSource");

        add(mappings, "JIMUQU_DISPLAY_TOOL_PROGRESS", "jimuqu.display.toolProgress");
        add(mappings, "JIMUQU_DISPLAY_SHOW_REASONING", "jimuqu.display.showReasoning");
        add(mappings, "JIMUQU_DISPLAY_TOOL_PREVIEW_LENGTH", "jimuqu.display.toolPreviewLength");
        add(mappings, "JIMUQU_DISPLAY_PROGRESS_THROTTLE_MS", "jimuqu.display.progressThrottleMs");

        add(mappings, "JIMUQU_GATEWAY_ALLOWED_USERS", "jimuqu.gateway.allowedUsers");
        add(mappings, "JIMUQU_GATEWAY_ALLOW_ALL_USERS", "jimuqu.gateway.allowAllUsers");
        add(mappings, "JIMUQU_GATEWAY_INJECTION_SECRET", "jimuqu.gateway.injectionSecret");
        add(mappings, "JIMUQU_GATEWAY_INJECTION_MAX_BODY_BYTES", "jimuqu.gateway.injectionMaxBodyBytes");
        add(mappings, "JIMUQU_GATEWAY_INJECTION_REPLAY_WINDOW_SECONDS", "jimuqu.gateway.injectionReplayWindowSeconds");

        add(mappings, "JIMUQU_AGENT_HEARTBEAT_ENABLED", "jimuqu.agent.heartbeat.enabled");
        add(mappings, "JIMUQU_AGENT_HEARTBEAT_INTERVAL_MINUTES", "jimuqu.agent.heartbeat.intervalMinutes");
        add(mappings, "JIMUQU_AGENT_HEARTBEAT_DELIVERY_MODE", "jimuqu.agent.heartbeat.deliveryMode");
        add(mappings, "JIMUQU_AGENT_HEARTBEAT_QUIET_TOKEN", "jimuqu.agent.heartbeat.quietToken");

        add(mappings, "JIMUQU_REACT_MAX_STEPS", "jimuqu.react.maxSteps");
        add(mappings, "JIMUQU_REACT_RETRY_MAX", "jimuqu.react.retryMax");
        add(mappings, "JIMUQU_REACT_RETRY_DELAY_MS", "jimuqu.react.retryDelayMs");
        add(mappings, "JIMUQU_REACT_DELEGATE_MAX_STEPS", "jimuqu.react.delegateMaxSteps");
        add(mappings, "JIMUQU_REACT_DELEGATE_RETRY_MAX", "jimuqu.react.delegateRetryMax");
        add(mappings, "JIMUQU_REACT_DELEGATE_RETRY_DELAY_MS", "jimuqu.react.delegateRetryDelayMs");
        add(mappings, "JIMUQU_REACT_SUMMARIZATION_ENABLED", "jimuqu.react.summarizationEnabled");
        add(mappings, "JIMUQU_REACT_SUMMARIZATION_MAX_MESSAGES", "jimuqu.react.summarizationMaxMessages");
        add(mappings, "JIMUQU_REACT_SUMMARIZATION_MAX_TOKENS", "jimuqu.react.summarizationMaxTokens");

        addChannelMappings(mappings, "FEISHU", "feishu",
                "appId", "appSecret", "websocketUrl", "botOpenId", "botUserId", "botName", "toolProgress");
        addChannelMappings(mappings, "DINGTALK", "dingtalk",
                "clientId", "clientSecret", "robotCode", "coolAppCode", "streamUrl", "toolProgress", "progressCardTemplateId");
        addChannelMappings(mappings, "WECOM", "wecom",
                "botId", "secret", "websocketUrl", "toolProgress");
        add(mappings, "JIMUQU_WECOM_GROUP_MEMBER_ALLOW_MAP_JSON", "jimuqu.channels.wecom.groupMemberAllowedUsers");
        addChannelMappings(mappings, "WEIXIN", "weixin",
                "token", "accountId", "baseUrl", "cdnBaseUrl", "longPollUrl", "splitMultilineMessages",
                "sendChunkDelaySeconds", "sendChunkRetries", "sendChunkRetryDelaySeconds", "toolProgress");

        add(mappings, "JIMUQU_UPDATE_REPO", "jimuqu.update.repo");
        add(mappings, "JIMUQU_UPDATE_RELEASE_API_URL", "jimuqu.update.releaseApiUrl");
        add(mappings, "JIMUQU_UPDATE_TAGS_API_URL", "jimuqu.update.tagsApiUrl");
        add(mappings, "JIMUQU_UPDATE_HTTP_PROXY", "jimuqu.update.httpProxy");

        add(mappings, "GITHUB_TOKEN", "jimuqu.integrations.github.token");
        add(mappings, "GH_TOKEN", "jimuqu.integrations.github.cliToken");
        add(mappings, "GITHUB_APP_ID", "jimuqu.integrations.github.appId");
        add(mappings, "GITHUB_APP_PRIVATE_KEY_PATH", "jimuqu.integrations.github.privateKeyPath");
        add(mappings, "GITHUB_APP_INSTALLATION_ID", "jimuqu.integrations.github.installationId");
        return mappings;
    }

    private static void addChannelMappings(Map<String, String> mappings,
                                           String envPrefix,
                                           String channelName,
                                           String... extraFields) {
        String base = "jimuqu.channels." + channelName + ".";
        add(mappings, "JIMUQU_" + envPrefix + "_ENABLED", base + "enabled");
        add(mappings, "JIMUQU_" + envPrefix + "_ALLOWED_USERS", base + "allowedUsers");
        add(mappings, "JIMUQU_" + envPrefix + "_ALLOW_ALL_USERS", base + "allowAllUsers");
        add(mappings, "JIMUQU_" + envPrefix + "_UNAUTHORIZED_DM_BEHAVIOR", base + "unauthorizedDmBehavior");
        add(mappings, "JIMUQU_" + envPrefix + "_DM_POLICY", base + "dmPolicy");
        add(mappings, "JIMUQU_" + envPrefix + "_GROUP_POLICY", base + "groupPolicy");
        add(mappings, "JIMUQU_" + envPrefix + "_GROUP_ALLOWED_USERS", base + "groupAllowedUsers");
        for (String field : Arrays.asList(extraFields)) {
            add(mappings, "JIMUQU_" + envPrefix + "_" + toEnvField(field), base + field);
        }
    }

    private static String toEnvField(String value) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                buffer.append('_');
            }
            buffer.append(Character.toUpperCase(ch));
        }
        return buffer.toString();
    }

    private static void add(Map<String, String> mappings, String key, String path) {
        mappings.put(key, path);
    }
}
