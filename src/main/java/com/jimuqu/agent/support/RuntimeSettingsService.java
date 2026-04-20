package com.jimuqu.agent.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.ChannelStatus;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.support.constants.AgentSettingConstants;
import com.jimuqu.agent.web.DashboardConfigService;
import com.jimuqu.agent.web.DashboardEnvService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时设置读取与修改服务。
 */
public class RuntimeSettingsService {
    private static final List<String> CONFIG_KEY_WHITELIST = Arrays.asList(
            "llm.provider",
            "llm.apiUrl",
            "llm.model",
            "llm.stream",
            "llm.reasoningEffort",
            "llm.temperature",
            "llm.maxTokens",
            "llm.contextWindowTokens",
            "scheduler.enabled",
            "scheduler.tickSeconds",
            "compression.enabled",
            "compression.thresholdPercent",
            "compression.summaryModel",
            "compression.protectHeadMessages",
            "compression.tailRatio",
            "learning.enabled",
            "learning.toolCallThreshold",
            "rollback.enabled",
            "rollback.maxCheckpointsPerSource",
            "gateway.allowedUsers",
            "gateway.allowAllUsers"
    );

    private static final List<String> CHANNEL_KEY_SUFFIX_WHITELIST = Arrays.asList(
            ".enabled",
            ".allowedUsers",
            ".allowAllUsers",
            ".unauthorizedDmBehavior",
            ".dmPolicy",
            ".groupPolicy",
            ".groupAllowedUsers",
            ".websocketUrl",
            ".streamUrl",
            ".coolAppCode",
            ".baseUrl",
            ".cdnBaseUrl",
            ".longPollUrl",
            ".splitMultilineMessages",
            ".sendChunkDelaySeconds",
            ".sendChunkRetries",
            ".sendChunkRetryDelaySeconds",
            ".botOpenId",
            ".botUserId",
            ".botName"
    );

    private final AppConfig appConfig;
    private final GlobalSettingRepository globalSettingRepository;
    private final DeliveryService deliveryService;
    private final DashboardConfigService dashboardConfigService;
    private final DashboardEnvService dashboardEnvService;

    public RuntimeSettingsService(AppConfig appConfig,
                                  GlobalSettingRepository globalSettingRepository,
                                  DeliveryService deliveryService,
                                  DashboardConfigService dashboardConfigService,
                                  DashboardEnvService dashboardEnvService) {
        this.appConfig = appConfig;
        this.globalSettingRepository = globalSettingRepository;
        this.deliveryService = deliveryService;
        this.dashboardConfigService = dashboardConfigService;
        this.dashboardEnvService = dashboardEnvService;
    }

    public ResolvedModel resolveEffectiveModel(SessionRecord session) {
        String provider = StrUtil.nullToEmpty(appConfig.getLlm().getProvider()).trim();
        String model = StrUtil.nullToEmpty(appConfig.getLlm().getModel()).trim();
        String override = session == null ? "" : StrUtil.nullToEmpty(session.getModelOverride()).trim();
        if (override.length() == 0) {
            return new ResolvedModel(provider, model, false);
        }
        if (override.contains(":")) {
            String[] parts = override.split(":", 2);
            return new ResolvedModel(StrUtil.nullToEmpty(parts[0]).trim(), StrUtil.nullToEmpty(parts[1]).trim(), true);
        }
        return new ResolvedModel(provider, override, true);
    }

    public String buildAgentRuntimePrompt(String sourceKey,
                                          SessionRecord session,
                                          List<String> enabledToolNames) {
        String[] parts = SourceKeySupport.split(sourceKey);
        ResolvedModel resolved = resolveEffectiveModel(session);
        List<String> channelStates = new ArrayList<String>();
        try {
            for (ChannelStatus status : deliveryService.statuses()) {
                if (status.getPlatform() == null) {
                    continue;
                }
                channelStates.add(status.getPlatform().name().toLowerCase()
                        + "(enabled=" + status.isEnabled()
                        + ",connected=" + status.isConnected() + ")");
            }
        } catch (Exception ignored) {
            // best effort
        }

        String activePersonality = "default";
        try {
            String stored = globalSettingRepository == null ? null : globalSettingRepository.get(AgentSettingConstants.ACTIVE_PERSONALITY);
            if (StrUtil.isNotBlank(stored)) {
                activePersonality = stored.trim();
            }
        } catch (Exception ignored) {
            // best effort
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append("[Agent Runtime]\n");
        buffer.append("agent_name=Jimuqu Agent\n");
        buffer.append("source_key=").append(StrUtil.nullToEmpty(sourceKey)).append('\n');
        buffer.append("platform=").append(StrUtil.nullToEmpty(parts[0])).append('\n');
        buffer.append("chat_id=").append(StrUtil.nullToEmpty(parts[1])).append('\n');
        buffer.append("user_id=").append(StrUtil.nullToEmpty(parts[2])).append('\n');
        buffer.append("session_id=").append(session == null ? "" : StrUtil.nullToEmpty(session.getSessionId())).append('\n');
        buffer.append("branch=").append(session == null ? "" : StrUtil.nullToEmpty(session.getBranchName())).append('\n');
        buffer.append("active_personality=").append(activePersonality).append('\n');
        buffer.append("default_provider=").append(StrUtil.nullToEmpty(appConfig.getLlm().getProvider())).append('\n');
        buffer.append("default_model=").append(StrUtil.nullToEmpty(appConfig.getLlm().getModel())).append('\n');
        buffer.append("effective_provider=").append(StrUtil.nullToEmpty(resolved.provider)).append('\n');
        buffer.append("effective_model=").append(StrUtil.nullToEmpty(resolved.model)).append('\n');
        buffer.append("has_session_model_override=").append(resolved.sessionOverride).append('\n');
        buffer.append("enabled_tools=").append(join(enabledToolNames)).append('\n');
        buffer.append("channels=").append(join(channelStates)).append('\n');
        buffer.append("runtime_home=").append(StrUtil.nullToEmpty(appConfig.getRuntime().getHome())).append('\n');
        buffer.append("Only change your own configuration through /model, config_set, or config_set_secret. Global changes take effect on the next message.");
        return buffer.toString();
    }

    public String describeModel(SessionRecord session) {
        ResolvedModel resolved = resolveEffectiveModel(session);
        StringBuilder buffer = new StringBuilder();
        buffer.append("current.provider=").append(StrUtil.nullToDefault(resolved.provider, "default")).append('\n');
        buffer.append("current.model=").append(StrUtil.nullToDefault(resolved.model, "default")).append('\n');
        buffer.append("current.apiUrl=").append(StrUtil.nullToDefault(appConfig.getLlm().getApiUrl(), "")).append('\n');
        buffer.append("session.override=").append(session == null ? "" : StrUtil.nullToDefault(session.getModelOverride(), "")).append('\n');
        buffer.append("global.provider=").append(StrUtil.nullToDefault(appConfig.getLlm().getProvider(), "")).append('\n');
        buffer.append("global.model=").append(StrUtil.nullToDefault(appConfig.getLlm().getModel(), "")).append('\n');
        return buffer.toString().trim();
    }

    public void setGlobalModel(String provider, String model) {
        if (StrUtil.isNotBlank(provider)) {
            persistConfigValue("llm.provider", provider.trim(), false);
        }
        if (StrUtil.isNotBlank(model)) {
            persistConfigValue("llm.model", model.trim(), false);
        }
    }

    public Object getConfigValue(String key) {
        ensureConfigKeyAllowed(key);
        return readNested(dashboardConfigService.getConfig(), key);
    }

    public void setConfigValue(String key, String rawValue) {
        ensureConfigKeyAllowed(key);
        persistConfigValue(key, parseValueForKey(key, rawValue), shouldReconnectChannelsForConfigKey(key));
    }

    public void setSecretValue(String envKey, String value) {
        dashboardEnvService.set(envKey, value, shouldReconnectChannelsForEnvKey(envKey));
    }

    private void ensureConfigKeyAllowed(String key) {
        if (CONFIG_KEY_WHITELIST.contains(key)) {
            return;
        }
        if (key != null && (key.startsWith("channels.feishu.") || key.startsWith("channels.dingtalk.")
                || key.startsWith("channels.wecom.") || key.startsWith("channels.weixin."))) {
            for (String suffix : CHANNEL_KEY_SUFFIX_WHITELIST) {
                if (key.endsWith(suffix)) {
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Unsupported config key: " + key);
    }

    private Object parseValueForKey(String key, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (key.endsWith(".enabled")
                || key.endsWith(".allowAllUsers")
                || key.endsWith(".splitMultilineMessages")
                || "llm.stream".equals(key)
                || "scheduler.enabled".equals(key)
                || "compression.enabled".equals(key)
                || "learning.enabled".equals(key)
                || "rollback.enabled".equals(key)
                || "gateway.allowAllUsers".equals(key)) {
            return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
        }
        if (key.endsWith("sendChunkRetries")
                || "scheduler.tickSeconds".equals(key)
                || "learning.toolCallThreshold".equals(key)
                || "rollback.maxCheckpointsPerSource".equals(key)
                || "compression.protectHeadMessages".equals(key)
                || "llm.maxTokens".equals(key)
                || "llm.contextWindowTokens".equals(key)) {
            return Integer.valueOf(value);
        }
        if (key.endsWith("sendChunkDelaySeconds")
                || key.endsWith("sendChunkRetryDelaySeconds")
                || "llm.temperature".equals(key)
                || "compression.thresholdPercent".equals(key)
                || "compression.tailRatio".equals(key)) {
            return Double.valueOf(value);
        }
        if (key.endsWith("allowedUsers")
                || key.endsWith("groupAllowedUsers")
                || "gateway.allowedUsers".equals(key)) {
            List<String> values = new ArrayList<String>();
            if (value.length() == 0) {
                return values;
            }
            for (String item : value.split(",")) {
                if (item != null && item.trim().length() > 0) {
                    values.add(item.trim());
                }
            }
            return values;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Object readNested(Map<String, Object> root, String key) {
        String[] parts = key.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder buffer = new StringBuilder();
        for (String value : values) {
            if (StrUtil.isBlank(value)) {
                continue;
            }
            if (buffer.length() > 0) {
                buffer.append(", ");
            }
            buffer.append(value.trim());
        }
        return buffer.toString();
    }

    private void persistConfigValue(String key, Object value, boolean reconnectChannels) {
        Map<String, Object> updates = new LinkedHashMap<String, Object>();
        updates.put(key, value);
        String envKey = dashboardConfigService.envNameFor(key);
        if (StrUtil.isNotBlank(envKey)) {
            dashboardConfigService.savePartialFlat(updates, false);
            dashboardEnvService.set(envKey, serializeValue(value), reconnectChannels);
        } else {
            dashboardConfigService.savePartialFlat(updates, reconnectChannels);
        }
    }

    private String serializeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List) {
            StringBuilder buffer = new StringBuilder();
            for (Object item : (List<?>) value) {
                if (item == null) {
                    continue;
                }
                String text = String.valueOf(item).trim();
                if (text.length() == 0) {
                    continue;
                }
                if (buffer.length() > 0) {
                    buffer.append(',');
                }
                buffer.append(text);
            }
            return buffer.toString();
        }
        return String.valueOf(value);
    }

    private boolean shouldReconnectChannelsForConfigKey(String key) {
        return key != null && key.startsWith("channels.");
    }

    private boolean shouldReconnectChannelsForEnvKey(String envKey) {
        return envKey != null && !envKey.startsWith("JIMUQU_LLM_");
    }

    public static class ResolvedModel {
        private final String provider;
        private final String model;
        private final boolean sessionOverride;

        public ResolvedModel(String provider, String model, boolean sessionOverride) {
            this.provider = provider;
            this.model = model;
            this.sessionOverride = sessionOverride;
        }

        public String getProvider() {
            return provider;
        }

        public String getModel() {
            return model;
        }

        public boolean isSessionOverride() {
            return sessionOverride;
        }
    }
}
