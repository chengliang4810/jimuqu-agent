package com.jimuqu.agent.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.config.RuntimeConfigResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 环境变量管理服务。
 */
public class DashboardRuntimeConfigService {
    private final RuntimeConfigResolver configResolver;
    private final List<ConfigItemDefinition> definitions;
    private final com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService;

    public DashboardRuntimeConfigService(AppConfig appConfig,
                               com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        this.configResolver = RuntimeConfigResolver.initialize(appConfig.getRuntime().getHome());
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        this.definitions = Arrays.asList(
                new ConfigItemDefinition("JIMUQU_LLM_PROVIDER_KEY", "默认模型 provider key", "provider", false, false, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_LLM_DEFAULT_MODEL", "全局默认模型覆盖", "provider", false, false, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_DEFAULT_PROVIDER_NAME", "默认 provider 名称", "provider", false, false, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_DEFAULT_PROVIDER_BASE_URL", "默认 provider 基础地址", "provider", false, false, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_DEFAULT_PROVIDER_MODEL", "默认 provider 模型", "provider", false, false, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_DEFAULT_PROVIDER_DIALECT", "默认 provider 协议方言", "provider", false, false, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_DEFAULT_PROVIDER_API_KEY", "默认 provider API 密钥", "provider", true, false, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_MAX_STEPS", "主代理最大推理步数", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_RETRY_MAX", "主代理决策重试次数", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_RETRY_DELAY_MS", "主代理决策重试延迟（毫秒）", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_DELEGATE_MAX_STEPS", "子代理最大推理步数", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_DELEGATE_RETRY_MAX", "子代理决策重试次数", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_DELEGATE_RETRY_DELAY_MS", "子代理决策重试延迟（毫秒）", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_SUMMARIZATION_ENABLED", "启用 ReAct 工作记忆摘要守卫", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_SUMMARIZATION_MAX_MESSAGES", "ReAct 摘要触发消息阈值", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_REACT_SUMMARIZATION_MAX_TOKENS", "ReAct 摘要触发 token 阈值", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_COMPRESSION_SUMMARY_MODEL", "压缩/工作记忆摘要模型", "provider", false, true, null, Arrays.asList("llm")),
                new ConfigItemDefinition("JIMUQU_FEISHU_ENABLED", "启用飞书渠道", "messaging", false, false, null, Arrays.asList("feishu")),
                new ConfigItemDefinition("JIMUQU_FEISHU_APP_ID", "飞书应用 ID", "messaging", false, false, null, Arrays.asList("feishu")),
                new ConfigItemDefinition("JIMUQU_FEISHU_APP_SECRET", "飞书应用密钥", "messaging", true, false, null, Arrays.asList("feishu")),
                new ConfigItemDefinition("JIMUQU_FEISHU_GROUP_ALLOWED_USERS", "飞书群聊 allowlist", "messaging", false, true, null, Arrays.asList("feishu")),
                new ConfigItemDefinition("JIMUQU_FEISHU_BOT_OPEN_ID", "飞书 bot Open ID", "messaging", false, true, null, Arrays.asList("feishu")),
                new ConfigItemDefinition("JIMUQU_FEISHU_BOT_USER_ID", "飞书 bot User ID", "messaging", false, true, null, Arrays.asList("feishu")),
                new ConfigItemDefinition("JIMUQU_DINGTALK_ENABLED", "启用钉钉渠道", "messaging", false, false, null, Arrays.asList("dingtalk")),
                new ConfigItemDefinition("JIMUQU_DINGTALK_CLIENT_ID", "钉钉客户端 ID", "messaging", false, false, null, Arrays.asList("dingtalk")),
                new ConfigItemDefinition("JIMUQU_DINGTALK_CLIENT_SECRET", "钉钉客户端密钥", "messaging", true, false, null, Arrays.asList("dingtalk")),
                new ConfigItemDefinition("JIMUQU_DINGTALK_ROBOT_CODE", "钉钉机器人编码", "messaging", true, false, null, Arrays.asList("dingtalk")),
                new ConfigItemDefinition("JIMUQU_DINGTALK_GROUP_ALLOWED_USERS", "钉钉群聊 allowlist", "messaging", false, true, null, Arrays.asList("dingtalk")),
                new ConfigItemDefinition("JIMUQU_WECOM_ENABLED", "启用企微渠道", "messaging", false, false, null, Arrays.asList("wecom")),
                new ConfigItemDefinition("JIMUQU_WECOM_BOT_ID", "企微机器人 ID", "messaging", false, false, null, Arrays.asList("wecom")),
                new ConfigItemDefinition("JIMUQU_WECOM_SECRET", "企微机器人密钥", "messaging", true, false, null, Arrays.asList("wecom")),
                new ConfigItemDefinition("JIMUQU_WECOM_GROUP_ALLOWED_USERS", "企微群聊 allowlist", "messaging", false, true, null, Arrays.asList("wecom")),
                new ConfigItemDefinition("JIMUQU_WEIXIN_ENABLED", "启用微信渠道", "messaging", false, false, null, Arrays.asList("weixin")),
                new ConfigItemDefinition("JIMUQU_WEIXIN_TOKEN", "微信令牌", "messaging", true, false, null, Arrays.asList("weixin")),
                new ConfigItemDefinition("JIMUQU_WEIXIN_ACCOUNT_ID", "微信 iLink accountId", "messaging", false, false, null, Arrays.asList("weixin")),
                new ConfigItemDefinition("JIMUQU_WEIXIN_GROUP_ALLOWED_USERS", "微信群聊 allowlist", "messaging", false, true, null, Arrays.asList("weixin")),
                new ConfigItemDefinition("JIMUQU_GATEWAY_INJECTION_SECRET", "HTTP gateway injection HMAC secret", "security", true, true, null, Arrays.asList("gateway")),
                new ConfigItemDefinition("JIMUQU_GATEWAY_INJECTION_MAX_BODY_BYTES", "HTTP gateway injection max body bytes", "security", false, true, null, Arrays.asList("gateway")),
                new ConfigItemDefinition("JIMUQU_GATEWAY_INJECTION_REPLAY_WINDOW_SECONDS", "HTTP gateway injection replay window seconds", "security", false, true, null, Arrays.asList("gateway")),
                new ConfigItemDefinition("JIMUQU_DASHBOARD_ACCESS_TOKEN", "Dashboard 和 API 访问令牌", "security", true, false, null, Arrays.asList("dashboard")),
                new ConfigItemDefinition("JIMUQU_UPDATE_REPO", "版本检查使用的 GitHub 仓库，格式 owner/repo", "runtime", false, true, null, Arrays.asList("version")),
                new ConfigItemDefinition("JIMUQU_UPDATE_RELEASE_API_URL", "自定义最新版本检查 API 地址，默认 GitHub releases/latest", "runtime", false, true, null, Arrays.asList("version")),
                new ConfigItemDefinition("JIMUQU_UPDATE_HTTP_PROXY", "版本检查 HTTP 代理地址，例如 http://proxy.example:7890", "runtime", false, true, null, Arrays.asList("version")),
                new ConfigItemDefinition("GITHUB_TOKEN", "Skills Hub 使用的 GitHub 访问令牌", "tool", true, true, "https://github.com/settings/tokens", Arrays.asList("skills_hub")),
                new ConfigItemDefinition("GH_TOKEN", "GitHub CLI 回退令牌", "tool", true, true, "https://github.com/settings/tokens", Arrays.asList("skills_hub")),
                new ConfigItemDefinition("GITHUB_APP_ID", "GitHub App ID", "tool", false, true, "https://github.com/settings/apps", Arrays.asList("skills_hub")),
                new ConfigItemDefinition("GITHUB_APP_PRIVATE_KEY_PATH", "GitHub App 私钥路径", "tool", false, true, "https://github.com/settings/apps", Arrays.asList("skills_hub")),
                new ConfigItemDefinition("GITHUB_APP_INSTALLATION_ID", "GitHub App 安装 ID", "tool", false, true, "https://github.com/settings/apps", Arrays.asList("skills_hub"))
        );
    }

    public Map<String, Object> getConfigItems() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (ConfigItemDefinition definition : definitions) {
            String value = configResolver.get(definition.key);

            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("is_set", StrUtil.isNotBlank(value));
            item.put("redacted_value", StrUtil.isBlank(value) ? null : redact(value));
            item.put("description", definition.description);
            item.put("url", definition.url);
            item.put("category", definition.category);
            item.put("is_password", definition.password);
            item.put("tools", definition.tools);
            item.put("advanced", definition.advanced);
            result.put(definition.key, item);
        }
        return result;
    }

    public Map<String, Object> reveal(String key) {
        ConfigItemDefinition definition = requireSupported(key);
        if (!definition.password) {
            throw new IllegalStateException("Runtime config item is not revealable: " + key);
        }
        String value = configResolver.get(key);
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("Runtime config item not set: " + key);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", key);
        result.put("value", value);
        return result;
    }

    public Map<String, Object> set(String key, String value) {
        return set(key, value, true);
    }

    public Map<String, Object> set(String key, String value, boolean reconnectChannels) {
        ensureSupported(key);
        configResolver.setFileValue(key, value);
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> remove(String key) {
        return remove(key, true);
    }

    public Map<String, Object> remove(String key, boolean reconnectChannels) {
        ensureSupported(key);
        configResolver.removeFileValue(key);
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    private void ensureSupported(String key) {
        requireSupported(key);
    }

    private ConfigItemDefinition requireSupported(String key) {
        for (ConfigItemDefinition definition : definitions) {
            if (definition.key.equals(key)) {
                return definition;
            }
        }
        throw new IllegalStateException("Unsupported runtime config item: " + key);
    }

    private String redact(String value) {
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private static class ConfigItemDefinition {
        private final String key;
        private final String description;
        private final String category;
        private final boolean password;
        private final boolean advanced;
        private final String url;
        private final List<String> tools;

        private ConfigItemDefinition(String key,
                                 String description,
                                 String category,
                                 boolean password,
                                 boolean advanced,
                                 String url,
                                 List<String> tools) {
            this.key = key;
            this.description = description;
            this.category = category;
            this.password = password;
            this.advanced = advanced;
            this.url = url;
            this.tools = tools;
        }
    }
}
