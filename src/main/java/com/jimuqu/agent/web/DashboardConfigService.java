package com.jimuqu.agent.web;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.config.RuntimeEnvResolver;
import org.noear.solon.core.util.Assert;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 配置读写与 schema 服务。
 */
public class DashboardConfigService {
    private static final List<String> PASSTHROUGH_PREFIXES = Arrays.asList("channels.wecom.groups.");

    private final AppConfig appConfig;
    private final RuntimeEnvResolver envResolver;
    private final com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService;
    private final Map<String, FieldDefinition> fields = new LinkedHashMap<String, FieldDefinition>();
    private final List<String> categoryOrder = Arrays.asList("general", "agent", "compression", "security", "messaging");

    public DashboardConfigService(AppConfig appConfig,
                                  com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        this.appConfig = appConfig;
        this.envResolver = RuntimeEnvResolver.getInstance();
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        registerFields();
    }

    public Map<String, Object> getConfig() {
        return toNestedFieldMap(resolveCurrentValues());
    }

    public Map<String, Object> getDefaults() {
        return toNestedFieldMap(resolveDefaultValues());
    }

    public Map<String, Object> getSchema() {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        Map<String, Object> fieldMaps = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, FieldDefinition> entry : fields.entrySet()) {
            fieldMaps.put(entry.getKey(), entry.getValue().toSchemaMap());
        }
        response.put("fields", fieldMaps);
        response.put("category_order", categoryOrder);
        return response;
    }

    public Map<String, Object> getRaw() {
        return Collections.<String, Object>singletonMap("yaml", dumpYaml(resolveCurrentValues()));
    }

    public String envNameFor(String key) {
        FieldDefinition definition = fields.get(key);
        return definition == null ? null : definition.envName;
    }

    public Map<String, Object> saveConfig(Map<String, Object> nestedConfig) {
        Map<String, Object> flat = flattenFieldMap(nestedConfig);
        validateKeys(flat.keySet());
        writeOverrideFile(flat);
        gatewayRuntimeRefreshService.refreshNow();
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> saveRaw(String yamlText) {
        Map<String, Object> flat = loadFieldMap(yamlText);
        validateKeys(flat.keySet());
        writeOverrideFile(flat);
        gatewayRuntimeRefreshService.refreshNow();
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> savePartialFlat(Map<String, Object> flatUpdates) {
        return savePartialFlat(flatUpdates, true);
    }

    public Map<String, Object> savePartialFlat(Map<String, Object> flatUpdates, boolean reconnectChannels) {
        validateKeys(flatUpdates.keySet());
        Map<String, Object> merged = mergeBaseValues();
        merged.putAll(flatUpdates);
        writeOverrideFile(merged);
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    private void registerFields() {
        addField(new FieldDefinition("llm.provider", "select", "general", "模型协议提供方")
                .envName("JIMUQU_LLM_PROVIDER")
                .options("openai", "openai-responses", "ollama", "gemini", "anthropic"));
        addField(new FieldDefinition("llm.apiUrl", "string", "general", "所选提供方的 API 地址")
                .envName("JIMUQU_LLM_API_URL"));
        addField(new FieldDefinition("llm.model", "string", "general", "默认模型名")
                .envName("JIMUQU_LLM_MODEL"));
        addField(new FieldDefinition("llm.stream", "boolean", "general", "是否启用流式输出")
                .envName("JIMUQU_LLM_STREAM"));
        addField(new FieldDefinition("llm.reasoningEffort", "select", "general", "默认推理强度")
                .envName("JIMUQU_LLM_REASONING_EFFORT")
                .options("minimal", "low", "medium", "high"));
        addField(new FieldDefinition("llm.temperature", "number", "general", "采样温度")
                .envName("JIMUQU_LLM_TEMPERATURE"));
        addField(new FieldDefinition("llm.maxTokens", "number", "general", "最大输出 token")
                .envName("JIMUQU_LLM_MAX_TOKENS"));
        addField(new FieldDefinition("llm.contextWindowTokens", "number", "general", "上下文窗口 token")
                .envName("JIMUQU_LLM_CONTEXT_WINDOW_TOKENS"));
        addField(new FieldDefinition("scheduler.enabled", "boolean", "general", "启用定时调度")
                .envName("JIMUQU_SCHEDULER_ENABLED"));
        addField(new FieldDefinition("scheduler.tickSeconds", "number", "general", "调度轮询周期（秒）")
                .envName("JIMUQU_SCHEDULER_TICK_SECONDS"));

        addField(new FieldDefinition("learning.enabled", "boolean", "agent", "启用主回复后的自动学习")
                .envName("JIMUQU_LEARNING_ENABLED"));
        addField(new FieldDefinition("learning.toolCallThreshold", "number", "agent", "触发学习所需的最少工具调用数")
                .envName("JIMUQU_LEARNING_TOOL_CALL_THRESHOLD"));
        addField(new FieldDefinition("rollback.enabled", "boolean", "agent", "启用 checkpoint 回滚")
                .envName("JIMUQU_ROLLBACK_ENABLED"));
        addField(new FieldDefinition("rollback.maxCheckpointsPerSource", "number", "agent", "每个来源保留的最大 checkpoint 数")
                .envName("JIMUQU_ROLLBACK_MAX_CHECKPOINTS_PER_SOURCE"));
        addField(new FieldDefinition("react.maxSteps", "number", "agent", "主代理最大推理步数")
                .envName("JIMUQU_REACT_MAX_STEPS"));
        addField(new FieldDefinition("react.retryMax", "number", "agent", "主代理决策重试次数")
                .envName("JIMUQU_REACT_RETRY_MAX"));
        addField(new FieldDefinition("react.retryDelayMs", "number", "agent", "主代理决策重试基础延迟（毫秒）")
                .envName("JIMUQU_REACT_RETRY_DELAY_MS"));
        addField(new FieldDefinition("react.delegateMaxSteps", "number", "agent", "子代理最大推理步数")
                .envName("JIMUQU_REACT_DELEGATE_MAX_STEPS"));
        addField(new FieldDefinition("react.delegateRetryMax", "number", "agent", "子代理决策重试次数")
                .envName("JIMUQU_REACT_DELEGATE_RETRY_MAX"));
        addField(new FieldDefinition("react.delegateRetryDelayMs", "number", "agent", "子代理决策重试基础延迟（毫秒）")
                .envName("JIMUQU_REACT_DELEGATE_RETRY_DELAY_MS"));
        addField(new FieldDefinition("agent.personalities.helpful.description", "string", "agent", "helpful 人格描述"));
        addField(new FieldDefinition("agent.personalities.helpful.systemPrompt", "text", "agent", "helpful 人格系统提示词"));
        addField(new FieldDefinition("agent.personalities.concise.description", "string", "agent", "concise 人格描述"));
        addField(new FieldDefinition("agent.personalities.concise.systemPrompt", "text", "agent", "concise 人格系统提示词"));
        addField(new FieldDefinition("agent.personalities.technical.description", "string", "agent", "technical 人格描述"));
        addField(new FieldDefinition("agent.personalities.technical.systemPrompt", "text", "agent", "technical 人格系统提示词"));
        addField(new FieldDefinition("agent.personalities.technical.tone", "string", "agent", "technical 人格语气"));
        addField(new FieldDefinition("agent.personalities.technical.style", "string", "agent", "technical 人格风格"));

        addField(new FieldDefinition("compression.enabled", "boolean", "compression", "启用上下文压缩")
                .envName("JIMUQU_COMPRESSION_ENABLED"));
        addField(new FieldDefinition("compression.thresholdPercent", "number", "compression", "触发压缩的阈值比例")
                .envName("JIMUQU_COMPRESSION_THRESHOLD_PERCENT"));
        addField(new FieldDefinition("compression.summaryModel", "string", "compression", "可选压缩摘要模型")
                .envName("JIMUQU_COMPRESSION_SUMMARY_MODEL"));
        addField(new FieldDefinition("compression.protectHeadMessages", "number", "compression", "头部保护消息数")
                .envName("JIMUQU_COMPRESSION_PROTECT_HEAD_MESSAGES"));
        addField(new FieldDefinition("compression.tailRatio", "number", "compression", "尾部保护比例")
                .envName("JIMUQU_COMPRESSION_TAIL_RATIO"));

        addField(new FieldDefinition("gateway.allowedUsers", "list", "security", "全局允许用户列表").envName("JIMUQU_GATEWAY_ALLOWED_USERS"));
        addField(new FieldDefinition("gateway.allowAllUsers", "boolean", "security", "是否全局允许所有用户").envName("JIMUQU_GATEWAY_ALLOW_ALL_USERS"));

        addChannelFields("feishu", "JIMUQU_FEISHU_ENABLED", "JIMUQU_FEISHU_ALLOWED_USERS", "JIMUQU_FEISHU_ALLOW_ALL_USERS", "JIMUQU_FEISHU_UNAUTHORIZED_DM_BEHAVIOR");
        addField(new FieldDefinition("channels.feishu.websocketUrl", "string", "messaging", "飞书 websocket 地址")
                .envName("JIMUQU_FEISHU_WEBSOCKET_URL"));
        addField(new FieldDefinition("channels.feishu.dmPolicy", "select", "messaging", "飞书私聊策略").options("open", "allowlist", "disabled", "pairing"));
        addField(new FieldDefinition("channels.feishu.groupPolicy", "select", "messaging", "飞书群聊策略").options("open", "allowlist", "disabled"));
        addField(new FieldDefinition("channels.feishu.groupAllowedUsers", "list", "messaging", "飞书群聊 allowlist").envName("JIMUQU_FEISHU_GROUP_ALLOWED_USERS"));
        addField(new FieldDefinition("channels.feishu.botOpenId", "string", "messaging", "飞书 bot Open ID").envName("JIMUQU_FEISHU_BOT_OPEN_ID"));
        addField(new FieldDefinition("channels.feishu.botUserId", "string", "messaging", "飞书 bot User ID").envName("JIMUQU_FEISHU_BOT_USER_ID"));
        addField(new FieldDefinition("channels.feishu.botName", "string", "messaging", "飞书 bot 展示名")
                .envName("JIMUQU_FEISHU_BOT_NAME"));

        addChannelFields("dingtalk", "JIMUQU_DINGTALK_ENABLED", "JIMUQU_DINGTALK_ALLOWED_USERS", "JIMUQU_DINGTALK_ALLOW_ALL_USERS", "JIMUQU_DINGTALK_UNAUTHORIZED_DM_BEHAVIOR");
        addField(new FieldDefinition("channels.dingtalk.coolAppCode", "string", "messaging", "可选钉钉 Cool App 编码")
                .envName("JIMUQU_DINGTALK_COOL_APP_CODE"));
        addField(new FieldDefinition("channels.dingtalk.streamUrl", "string", "messaging", "钉钉 stream 地址")
                .envName("JIMUQU_DINGTALK_STREAM_URL"));
        addField(new FieldDefinition("channels.dingtalk.dmPolicy", "select", "messaging", "钉钉私聊策略").options("open", "allowlist", "disabled", "pairing"));
        addField(new FieldDefinition("channels.dingtalk.groupPolicy", "select", "messaging", "钉钉群聊策略").options("open", "allowlist", "disabled"));
        addField(new FieldDefinition("channels.dingtalk.groupAllowedUsers", "list", "messaging", "钉钉群聊 allowlist").envName("JIMUQU_DINGTALK_GROUP_ALLOWED_USERS"));

        addChannelFields("wecom", "JIMUQU_WECOM_ENABLED", "JIMUQU_WECOM_ALLOWED_USERS", "JIMUQU_WECOM_ALLOW_ALL_USERS", "JIMUQU_WECOM_UNAUTHORIZED_DM_BEHAVIOR");
        addField(new FieldDefinition("channels.wecom.websocketUrl", "string", "messaging", "企微 websocket 地址")
                .envName("JIMUQU_WECOM_WEBSOCKET_URL"));
        addField(new FieldDefinition("channels.wecom.dmPolicy", "select", "messaging", "企微私聊策略").options("open", "allowlist", "disabled", "pairing"));
        addField(new FieldDefinition("channels.wecom.groupPolicy", "select", "messaging", "企微群聊策略").options("open", "allowlist", "disabled"));
        addField(new FieldDefinition("channels.wecom.groupAllowedUsers", "list", "messaging", "企微群聊 allowlist").envName("JIMUQU_WECOM_GROUP_ALLOWED_USERS"));

        addChannelFields("weixin", "JIMUQU_WEIXIN_ENABLED", "JIMUQU_WEIXIN_ALLOWED_USERS", "JIMUQU_WEIXIN_ALLOW_ALL_USERS", "JIMUQU_WEIXIN_UNAUTHORIZED_DM_BEHAVIOR");
        addField(new FieldDefinition("channels.weixin.accountId", "string", "messaging", "微信 iLink accountId").envName("JIMUQU_WEIXIN_ACCOUNT_ID"));
        addField(new FieldDefinition("channels.weixin.baseUrl", "string", "messaging", "微信 iLink API 地址")
                .envName("JIMUQU_WEIXIN_BASE_URL"));
        addField(new FieldDefinition("channels.weixin.cdnBaseUrl", "string", "messaging", "微信 CDN 地址")
                .envName("JIMUQU_WEIXIN_CDN_BASE_URL"));
        addField(new FieldDefinition("channels.weixin.longPollUrl", "string", "messaging", "微信 long-poll 地址")
                .envName("JIMUQU_WEIXIN_LONG_POLL_URL"));
        addField(new FieldDefinition("channels.weixin.dmPolicy", "select", "messaging", "微信私聊策略").options("open", "allowlist", "disabled", "pairing"));
        addField(new FieldDefinition("channels.weixin.groupPolicy", "select", "messaging", "微信群聊策略").options("open", "allowlist", "disabled"));
        addField(new FieldDefinition("channels.weixin.groupAllowedUsers", "list", "messaging", "微信群聊 allowlist").envName("JIMUQU_WEIXIN_GROUP_ALLOWED_USERS"));
        addField(new FieldDefinition("channels.weixin.splitMultilineMessages", "boolean", "messaging", "微信多行消息拆分")
                .envName("JIMUQU_WEIXIN_SPLIT_MULTILINE_MESSAGES"));
        addField(new FieldDefinition("channels.weixin.sendChunkDelaySeconds", "number", "messaging", "微信分片发送间隔（秒）")
                .envName("JIMUQU_WEIXIN_SEND_CHUNK_DELAY_SECONDS"));
        addField(new FieldDefinition("channels.weixin.sendChunkRetries", "number", "messaging", "微信分片重试次数")
                .envName("JIMUQU_WEIXIN_SEND_CHUNK_RETRIES"));
        addField(new FieldDefinition("channels.weixin.sendChunkRetryDelaySeconds", "number", "messaging", "微信分片重试间隔（秒）")
                .envName("JIMUQU_WEIXIN_SEND_CHUNK_RETRY_DELAY_SECONDS"));
    }

    private void addChannelFields(String name, String enabledEnv, String allowedUsersEnv, String allowAllEnv, String behaviorEnv) {
        FieldDefinition enabledField = new FieldDefinition("channels." + name + ".enabled", "boolean", "messaging", channelLabel(name) + "渠道开关");
        if (enabledEnv != null) {
            enabledField.envName(enabledEnv);
        }
        addField(enabledField);
        addField(new FieldDefinition("channels." + name + ".allowedUsers", "list", "messaging", channelLabel(name) + "允许用户列表").envName(allowedUsersEnv));
        addField(new FieldDefinition("channels." + name + ".allowAllUsers", "boolean", "messaging", channelLabel(name) + "是否允许所有用户").envName(allowAllEnv));
        addField(new FieldDefinition("channels." + name + ".unauthorizedDmBehavior", "select", "messaging", channelLabel(name) + "未授权私聊行为")
                .envName(behaviorEnv)
                .options("pair", "ignore"));
    }

    private String channelLabel(String name) {
        if ("feishu".equals(name)) {
            return "飞书";
        }
        if ("dingtalk".equals(name)) {
            return "钉钉";
        }
        if ("wecom".equals(name)) {
            return "企微";
        }
        if ("weixin".equals(name)) {
            return "微信";
        }
        return name;
    }

    private void addField(FieldDefinition definition) {
        fields.put(definition.key, definition);
    }

    private Map<String, Object> resolveCurrentValues() {
        Map<String, Object> defaults = resolveDefaultValues();
        Map<String, Object> overrides = loadOverrideFields();
        Map<String, Object> current = new LinkedHashMap<String, Object>();
        for (FieldDefinition field : fields.values()) {
            Object value = overrides.containsKey(field.key) ? overrides.get(field.key) : defaults.get(field.key);
            if (field.envName != null) {
                String envValue = envResolver.get(field.envName);
                if (StrUtil.isNotBlank(envValue)) {
                    value = parseTypedValue(field.type, envValue);
                }
            }
            current.put(field.key, value);
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            if (isSupportedPassthroughKey(entry.getKey())) {
                current.put(entry.getKey(), entry.getValue());
            }
        }
        return current;
    }

    private Map<String, Object> resolveDefaultValues() {
        Map<String, Object> raw = loadFieldMap(loadClasspathAppYaml());
        Map<String, Object> defaults = new LinkedHashMap<String, Object>();
        for (FieldDefinition field : fields.values()) {
            defaults.put(field.key, raw.get(field.key));
        }
        return defaults;
    }

    private String loadClasspathAppYaml() {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("app.yml");
        if (stream == null) {
            return "";
        }
        return IoUtil.read(stream, StandardCharsets.UTF_8);
    }

    private Map<String, Object> loadOverrideFields() {
        File overrideFile = new File(appConfig.getRuntime().getConfigOverrideFile());
        if (!overrideFile.exists()) {
            return Collections.emptyMap();
        }
        return loadFieldMap(FileUtil.readUtf8String(overrideFile));
    }

    private Map<String, Object> loadFieldMap(String yamlText) {
        if (StrUtil.isBlank(yamlText)) {
            return Collections.emptyMap();
        }

        Object parsed = new Yaml().load(yamlText);
        if (!(parsed instanceof Map)) {
            return Collections.emptyMap();
        }

        Map<String, Object> flattened = new LinkedHashMap<String, Object>();
        flatten("", (Map<?, ?>) parsed, flattened);

        Map<String, Object> fieldValues = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : flattened.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("jimuqu.")) {
                key = key.substring("jimuqu.".length());
            }
            if (fields.containsKey(key) || isSupportedPassthroughKey(key)) {
                fieldValues.put(key, entry.getValue());
            }
        }
        return fieldValues;
    }

    private void flatten(String prefix, Map<?, ?> input, Map<String, Object> output) {
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

    private Map<String, Object> flattenFieldMap(Map<String, Object> nested) {
        Assert.notNull(nested, "config body is required");
        Map<String, Object> output = new LinkedHashMap<String, Object>();
        flattenNested("", nested, output);

        Map<String, Object> filtered = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : output.entrySet()) {
            if (fields.containsKey(entry.getKey()) || isSupportedPassthroughKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private void flattenNested(String prefix, Map<String, Object> input, Map<String, Object> output) {
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = prefix.length() == 0 ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenNested(key, (Map<String, Object>) value, output);
            } else {
                output.put(key, value);
            }
        }
    }

    private Map<String, Object> toNestedFieldMap(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            setNestedValue(root, entry.getKey(), entry.getValue());
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(Map<String, Object> root, String key, Object value) {
        String[] parts = key.split("\\.");
        Map<String, Object> cursor = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object current = cursor.get(parts[i]);
            if (!(current instanceof Map)) {
                current = new LinkedHashMap<String, Object>();
                cursor.put(parts[i], current);
            }
            cursor = (Map<String, Object>) current;
        }
        cursor.put(parts[parts.length - 1], value);
    }

    private void validateKeys(Iterable<String> keys) {
        for (String key : keys) {
            if (key.startsWith("runtime.") || key.startsWith("jimuqu.runtime.")) {
                throw new IllegalStateException("jimuqu.runtime.* is not editable from the dashboard");
            }
            if (!fields.containsKey(key) && !isSupportedPassthroughKey(key)) {
                throw new IllegalStateException("Unsupported config key: " + key);
            }
        }
    }

    private Map<String, Object> mergeBaseValues() {
        return new LinkedHashMap<String, Object>(loadOverrideFields());
    }

    private boolean isSupportedPassthroughKey(String key) {
        for (String prefix : PASSTHROUGH_PREFIXES) {
            if (key != null && key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeOverrideFile(Map<String, Object> fieldValues) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        Map<String, Object> jimuqu = new LinkedHashMap<String, Object>();
        root.put("jimuqu", jimuqu);
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            setNestedValue(jimuqu, entry.getKey(), entry.getValue());
        }

        FileUtil.mkParentDirs(appConfig.getRuntime().getConfigOverrideFile());
        FileUtil.writeUtf8String(dump(root), new File(appConfig.getRuntime().getConfigOverrideFile()));
    }

    private String dumpYaml(Map<String, Object> fieldValues) {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        Map<String, Object> jimuqu = new LinkedHashMap<String, Object>();
        root.put("jimuqu", jimuqu);
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            setNestedValue(jimuqu, entry.getKey(), entry.getValue());
        }
        return dump(root);
    }

    private String dump(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        return new Yaml(options).dump(root);
    }

    private Object parseTypedValue(String type, String raw) {
        if ("boolean".equals(type)) {
            return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
        }
        if ("number".equals(type)) {
            try {
                return raw.contains(".") ? Double.valueOf(raw) : Integer.valueOf(raw);
            } catch (Exception e) {
                return 0;
            }
        }
        if ("list".equals(type)) {
            List<String> values = new ArrayList<String>();
            for (String item : raw.split(",")) {
                if (StrUtil.isNotBlank(item)) {
                    values.add(item.trim());
                }
            }
            return values;
        }
        return raw;
    }

    private static class FieldDefinition {
        private final String key;
        private final String type;
        private final String category;
        private final String description;
        private String envName;
        private List<String> options = Collections.emptyList();

        private FieldDefinition(String key, String type, String category, String description) {
            this.key = key;
            this.type = type;
            this.category = category;
            this.description = description;
        }

        private FieldDefinition envName(String envName) {
            this.envName = envName;
            return this;
        }

        private FieldDefinition options(String... values) {
            this.options = Arrays.asList(values);
            return this;
        }

        private Map<String, Object> toSchemaMap() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("type", type);
            result.put("category", category);
            result.put("description", description);
            if (!options.isEmpty()) {
                result.put("options", options);
            }
            return result;
        }
    }
}
