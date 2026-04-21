package com.jimuqu.agent.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.config.RuntimeEnvResolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 环境变量管理服务。
 */
public class DashboardEnvService {
    private final RuntimeEnvResolver envResolver;
    private final List<EnvVarDefinition> definitions;
    private final com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService;

    public DashboardEnvService(AppConfig appConfig,
                               com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        this.envResolver = RuntimeEnvResolver.initialize(appConfig.getRuntime().getHome());
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        this.definitions = Arrays.asList(
                new EnvVarDefinition("JIMUQU_LLM_PROVIDER", "默认模型 provider 覆盖", "provider", false, false, null, Arrays.asList("llm")),
                new EnvVarDefinition("JIMUQU_LLM_API_URL", "默认模型 API 地址覆盖", "provider", false, false, null, Arrays.asList("llm")),
                new EnvVarDefinition("JIMUQU_LLM_MODEL", "默认模型名覆盖", "provider", false, false, null, Arrays.asList("llm")),
                new EnvVarDefinition("JIMUQU_LLM_API_KEY", "默认模型 API 密钥", "provider", true, false, null, Arrays.asList("llm")),
                new EnvVarDefinition("JIMUQU_FEISHU_ENABLED", "启用飞书渠道", "messaging", false, false, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_FEISHU_APP_ID", "飞书应用 ID", "messaging", false, false, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_FEISHU_APP_SECRET", "飞书应用密钥", "messaging", true, false, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_FEISHU_GROUP_ALLOWED_USERS", "飞书群聊 allowlist", "messaging", false, true, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_FEISHU_BOT_OPEN_ID", "飞书 bot Open ID", "messaging", false, true, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_FEISHU_BOT_USER_ID", "飞书 bot User ID", "messaging", false, true, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_DINGTALK_ENABLED", "启用钉钉渠道", "messaging", false, false, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_DINGTALK_CLIENT_ID", "钉钉客户端 ID", "messaging", false, false, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_DINGTALK_CLIENT_SECRET", "钉钉客户端密钥", "messaging", true, false, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_DINGTALK_ROBOT_CODE", "钉钉机器人编码", "messaging", true, false, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_DINGTALK_GROUP_ALLOWED_USERS", "钉钉群聊 allowlist", "messaging", false, true, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_WECOM_ENABLED", "启用企微渠道", "messaging", false, false, null, Arrays.asList("wecom")),
                new EnvVarDefinition("JIMUQU_WECOM_BOT_ID", "企微机器人 ID", "messaging", false, false, null, Arrays.asList("wecom")),
                new EnvVarDefinition("JIMUQU_WECOM_SECRET", "企微机器人密钥", "messaging", true, false, null, Arrays.asList("wecom")),
                new EnvVarDefinition("JIMUQU_WECOM_GROUP_ALLOWED_USERS", "企微群聊 allowlist", "messaging", false, true, null, Arrays.asList("wecom")),
                new EnvVarDefinition("JIMUQU_WEIXIN_ENABLED", "启用微信渠道", "messaging", false, false, null, Arrays.asList("weixin")),
                new EnvVarDefinition("JIMUQU_WEIXIN_TOKEN", "微信令牌", "messaging", true, false, null, Arrays.asList("weixin")),
                new EnvVarDefinition("JIMUQU_WEIXIN_ACCOUNT_ID", "微信 iLink accountId", "messaging", false, false, null, Arrays.asList("weixin")),
                new EnvVarDefinition("JIMUQU_WEIXIN_GROUP_ALLOWED_USERS", "微信群聊 allowlist", "messaging", false, true, null, Arrays.asList("weixin")),
                new EnvVarDefinition("JIMUQU_UPDATE_REPO", "版本检查使用的 GitHub 仓库，格式 owner/repo", "runtime", false, true, null, Arrays.asList("version")),
                new EnvVarDefinition("JIMUQU_UPDATE_RELEASE_API_URL", "自定义最新版本检查 API 地址，默认 GitHub releases/latest", "runtime", false, true, null, Arrays.asList("version")),
                new EnvVarDefinition("JIMUQU_UPDATE_HTTP_PROXY", "版本检查 HTTP 代理地址，例如 http://proxy.example:7890", "runtime", false, true, null, Arrays.asList("version")),
                new EnvVarDefinition("GITHUB_TOKEN", "Skills Hub 使用的 GitHub 访问令牌", "tool", true, true, "https://github.com/settings/tokens", Arrays.asList("skills_hub")),
                new EnvVarDefinition("GH_TOKEN", "GitHub CLI 回退令牌", "tool", true, true, "https://github.com/settings/tokens", Arrays.asList("skills_hub")),
                new EnvVarDefinition("GITHUB_APP_ID", "GitHub App ID", "tool", false, true, "https://github.com/settings/apps", Arrays.asList("skills_hub")),
                new EnvVarDefinition("GITHUB_APP_PRIVATE_KEY_PATH", "GitHub App 私钥路径", "tool", false, true, "https://github.com/settings/apps", Arrays.asList("skills_hub")),
                new EnvVarDefinition("GITHUB_APP_INSTALLATION_ID", "GitHub App 安装 ID", "tool", false, true, "https://github.com/settings/apps", Arrays.asList("skills_hub"))
        );
    }

    public Map<String, Object> getEnvVars() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (EnvVarDefinition definition : definitions) {
            String value = envResolver.get(definition.key);

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
        String value = envResolver.get(key);
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("Environment variable not set: " + key);
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
        envResolver.setFileValue(key, value);
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
        envResolver.removeFileValue(key);
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    private void ensureSupported(String key) {
        for (EnvVarDefinition definition : definitions) {
            if (definition.key.equals(key)) {
                return;
            }
        }
        throw new IllegalStateException("Unsupported environment variable: " + key);
    }

    private String redact(String value) {
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    private static class EnvVarDefinition {
        private final String key;
        private final String description;
        private final String category;
        private final boolean password;
        private final boolean advanced;
        private final String url;
        private final List<String> tools;

        private EnvVarDefinition(String key,
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
