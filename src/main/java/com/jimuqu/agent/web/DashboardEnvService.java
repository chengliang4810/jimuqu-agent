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

    public DashboardEnvService(AppConfig appConfig) {
        this.envResolver = RuntimeEnvResolver.initialize(appConfig.getRuntime().getHome());
        this.definitions = Arrays.asList(
                new EnvVarDefinition("JIMUQU_LLM_API_KEY", "默认模型 API 密钥", "provider", true, false, null, Arrays.asList("llm")),
                new EnvVarDefinition("JIMUQU_FEISHU_APP_ID", "飞书应用 ID", "messaging", false, false, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_FEISHU_APP_SECRET", "飞书应用密钥", "messaging", true, false, null, Arrays.asList("feishu")),
                new EnvVarDefinition("JIMUQU_DINGTALK_CLIENT_ID", "钉钉客户端 ID", "messaging", false, false, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_DINGTALK_CLIENT_SECRET", "钉钉客户端密钥", "messaging", true, false, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_DINGTALK_ROBOT_CODE", "钉钉机器人编码", "messaging", true, false, null, Arrays.asList("dingtalk")),
                new EnvVarDefinition("JIMUQU_WECOM_BOT_ID", "企微机器人 ID", "messaging", false, false, null, Arrays.asList("wecom")),
                new EnvVarDefinition("JIMUQU_WECOM_SECRET", "企微机器人密钥", "messaging", true, false, null, Arrays.asList("wecom")),
                new EnvVarDefinition("JIMUQU_WEIXIN_TOKEN", "微信令牌", "messaging", true, false, null, Arrays.asList("weixin")),
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
        ensureSupported(key);
        envResolver.setFileValue(key, value);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    public Map<String, Object> remove(String key) {
        ensureSupported(key);
        envResolver.removeFileValue(key);
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
