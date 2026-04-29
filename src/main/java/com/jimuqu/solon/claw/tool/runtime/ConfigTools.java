package com.jimuqu.solon.claw.tool.runtime;

import com.jimuqu.solon.claw.support.RuntimeSettingsService;
import lombok.RequiredArgsConstructor;
import org.noear.snack4.ONode;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.annotation.Param;

/** 运行时配置工具。 */
@RequiredArgsConstructor
public class ConfigTools {
    private final RuntimeSettingsService runtimeSettingsService;

    @ToolMapping(
            name = "config_get",
            description =
                    "Read a whitelisted runtime config key, such as llm.model or channels.weixin.enabled.")
    public String configGet(@Param(name = "key", description = "配置键，例如 llm.model") String key) {
        try {
            return new ONode()
                    .set("success", true)
                    .set("key", key)
                    .set("value", runtimeSettingsService.getConfigValue(key))
                    .toJson();
        } catch (Exception e) {
            return error(e);
        }
    }

    @ToolMapping(
            name = "config_set",
            description =
                    "Update a whitelisted runtime config key. Global config changes take effect on the next message.")
    public String configSet(
            @Param(name = "key", description = "配置键，例如 llm.model 或 channels.weixin.enabled")
                    String key,
            @Param(name = "value", description = "新的配置值，列表键使用逗号分隔") String value) {
        try {
            runtimeSettingsService.setConfigValue(key, value);
            return new ONode()
                    .set("success", true)
                    .set("key", key)
                    .set("value", runtimeSettingsService.getConfigValue(key))
                    .set("note", "takes effect on the next message")
                    .toJson();
        } catch (Exception e) {
            return error(e);
        }
    }

    @ToolMapping(
            name = "config_set_secret",
            description =
                    "Update a whitelisted runtime secret key, such as providers.default.apiKey.")
    public String configSetSecret(
            @Param(name = "key", description = "配置键，例如 providers.default.apiKey") String key,
            @Param(name = "value", description = "新的密钥值") String value) {
        try {
            runtimeSettingsService.setSecretValue(key, value);
            return new ONode()
                    .set("success", true)
                    .set("key", key)
                    .set("note", "takes effect on the next message")
                    .toJson();
        } catch (Exception e) {
            return error(e);
        }
    }

    private String error(Exception e) {
        return new ONode()
                .set("success", false)
                .set(
                        "error",
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                .toJson();
    }

    @RequiredArgsConstructor
    public static class ConfigGetTool {
        private final ConfigTools delegate;

        @ToolMapping(
                name = "config_get",
                description =
                        "Read a whitelisted runtime config key, such as llm.model or channels.weixin.enabled.")
        public String configGet(@Param(name = "key", description = "配置键，例如 llm.model") String key) {
            return delegate.configGet(key);
        }
    }

    @RequiredArgsConstructor
    public static class ConfigSetTool {
        private final ConfigTools delegate;

        @ToolMapping(
                name = "config_set",
                description =
                        "Update a whitelisted runtime config key. Global config changes take effect on the next message.")
        public String configSet(
                @Param(name = "key", description = "配置键，例如 llm.model 或 channels.weixin.enabled")
                        String key,
                @Param(name = "value", description = "新的配置值，列表键使用逗号分隔") String value) {
            return delegate.configSet(key, value);
        }
    }

    @RequiredArgsConstructor
    public static class ConfigSetSecretTool {
        private final ConfigTools delegate;

        @ToolMapping(
                name = "config_set_secret",
                description =
                        "Update a whitelisted runtime secret key, such as providers.default.apiKey.")
        public String configSetSecret(
                @Param(name = "key", description = "配置键，例如 providers.default.apiKey") String key,
                @Param(name = "value", description = "新的密钥值") String value) {
            return delegate.configSetSecret(key, value);
        }
    }
}
