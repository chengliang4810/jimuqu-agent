package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.support.SecretValueGuard;
import com.jimuqu.solon.claw.tool.runtime.port.RuntimeConfigManagementPort;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigCatalog.ConfigItemDefinition;
import com.jimuqu.solon.claw.web.profile.DashboardProfileContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dashboard 工作区配置管理门面。 */
public class DashboardRuntimeConfigService implements RuntimeConfigManagementPort {
    /** 受支持配置项及展示元数据目录。 */
    private final DashboardRuntimeConfigCatalog catalog;

    /** Profile、配置文件、变更锁与运行时刷新存储服务。 */
    private final DashboardRuntimeConfigStore store;

    /**
     * 创建控制台工作区配置服务实例，并注入运行所需依赖。
     *
     * @param appConfig 应用运行配置。
     * @param gatewayRuntimeRefreshService 网关工作区配置刷新服务依赖。
     */
    public DashboardRuntimeConfigService(
            AppConfig appConfig,
            com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
                    gatewayRuntimeRefreshService) {
        this(appConfig, gatewayRuntimeRefreshService, null);
    }

    /**
     * 创建支持 Profile 作用域的工作区配置服务。
     *
     * @param appConfig 当前 JVM 配置。
     * @param gatewayRuntimeRefreshService 当前 JVM 网关刷新服务。
     * @param profileContext Dashboard Profile 请求上下文。
     */
    public DashboardRuntimeConfigService(
            AppConfig appConfig,
            com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService
                    gatewayRuntimeRefreshService,
            DashboardProfileContext profileContext) {
        this.catalog = new DashboardRuntimeConfigCatalog();
        this.store =
                new DashboardRuntimeConfigStore(
                        appConfig, gatewayRuntimeRefreshService, profileContext);
    }

    /**
     * 读取当前 Profile 的配置项。
     *
     * @return 已脱敏配置项。
     */
    @Override
    public Map<String, Object> getConfigItems() {
        return getConfigItems(null);
    }

    /**
     * 读取指定 Profile 的工作区配置项。
     *
     * @param profile Profile 名。
     * @return 已脱敏配置项。
     */
    public Map<String, Object> getConfigItems(String profile) {
        Map<String, String> values = store.readAll(catalog.keys(), profile);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (ConfigItemDefinition definition : catalog.definitions()) {
            String value = values.get(definition.key);
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

    /**
     * 判断配置键是否属于敏感配置，供 HTTP 审计边界在写入前分类。
     *
     * @param key 配置键。
     * @return 仅当受支持配置项标记为密钥时返回 true。
     */
    boolean isSecret(String key) {
        return catalog.require(key).password;
    }

    /**
     * 解析审计事件应记录的实际 Profile 名。
     *
     * @param profile 请求指定的 Profile 名。
     * @return 已校验并规范化的实际 Profile 名。
     */
    String resolveProfileName(String profile) {
        return store.resolveProfileName(profile);
    }

    /**
     * 读取当前 Profile 的单个密钥明文。
     *
     * @param key 配置键。
     * @return 明文值。
     */
    public Map<String, Object> reveal(String key) {
        return reveal(key, null);
    }

    /**
     * 读取指定 Profile 的单个密钥明文。
     *
     * @param key 配置键。
     * @param profile Profile 名。
     * @return 明文值。
     */
    public Map<String, Object> reveal(String key, String profile) {
        ConfigItemDefinition definition = catalog.require(key);
        if (!definition.password) {
            throw new IllegalStateException("Runtime config item is not revealable: " + key);
        }
        String value = store.read(key, profile);
        if (StrUtil.isBlank(value)) {
            throw new IllegalStateException("Runtime config item not set: " + key);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", key);
        result.put("value", value);
        return result;
    }

    /**
     * 写入当前 Profile 的配置值并重连渠道。
     *
     * @param key 配置键。
     * @param value 配置值。
     * @return 保存结果。
     */
    public Map<String, Object> set(String key, String value) {
        return set(key, value, true);
    }

    /**
     * 写入指定 Profile 的配置值。
     *
     * @param key 配置键。
     * @param value 配置值。
     * @param profile Profile 名。
     * @return 保存结果。
     */
    public Map<String, Object> set(String key, String value, String profile) {
        return set(key, value, true, profile);
    }

    /**
     * 写入当前 Profile 的配置值并控制是否重连渠道。
     *
     * @param key 配置键。
     * @param value 配置值。
     * @param reconnectChannels 是否重连渠道。
     * @return 保存结果。
     */
    public Map<String, Object> set(String key, String value, boolean reconnectChannels) {
        return set(key, value, reconnectChannels, null);
    }

    /**
     * 写入指定 Profile 的配置值并控制当前 JVM 是否重连渠道。
     *
     * @param key 配置键。
     * @param value 配置值。
     * @param reconnectChannels 是否重连当前 JVM 渠道。
     * @param profile Profile 名。
     * @return 保存结果。
     */
    public Map<String, Object> set(
            String key, String value, boolean reconnectChannels, String profile) {
        ConfigItemDefinition definition = catalog.require(key);
        if (definition.password) {
            return updateSecret(key, value, reconnectChannels, profile);
        }
        return writeNonSecret(key, value, reconnectChannels, profile);
    }

    /**
     * 写入当前 Profile 的非密钥配置。
     *
     * @param key 配置键。
     * @param value 配置值。
     * @param reconnectChannels 是否重连渠道。
     * @return 保存结果。
     */
    @Override
    public Map<String, Object> writeNonSecret(String key, String value, boolean reconnectChannels) {
        return writeNonSecret(key, value, reconnectChannels, null);
    }

    /** 写入指定 Profile 的非密钥配置。 */
    private Map<String, Object> writeNonSecret(
            String key, String value, boolean reconnectChannels, String profile) {
        ConfigItemDefinition definition = catalog.require(key);
        if (definition.password) {
            throw new IllegalArgumentException(key + " 是密钥配置，请使用 secret update/reveal 流程。");
        }
        store.write(key, value, reconnectChannels, profile);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    /**
     * 更新当前 Profile 的密钥配置。
     *
     * @param key 配置键。
     * @param value 配置值。
     * @param reconnectChannels 是否重连渠道。
     * @return 保存结果。
     */
    public Map<String, Object> updateSecret(String key, String value, boolean reconnectChannels) {
        return updateSecret(key, value, reconnectChannels, null);
    }

    /** 写入指定 Profile 的密钥配置。 */
    private Map<String, Object> updateSecret(
            String key, String value, boolean reconnectChannels, String profile) {
        ConfigItemDefinition definition = catalog.require(key);
        if (!definition.password) {
            throw new IllegalArgumentException(key + " 不是密钥配置，请使用普通配置写入流程。");
        }
        if (SecretValueGuard.isPlaceholderSecret(value)) {
            throw new IllegalArgumentException(key + " 不能使用示例或占位符密钥。");
        }
        store.write(key, value, reconnectChannels, profile);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    /**
     * 删除当前 Profile 的配置值并重连渠道。
     *
     * @param key 配置键。
     * @return 删除结果。
     */
    @Override
    public Map<String, Object> remove(String key) {
        return remove(key, true);
    }

    /**
     * 删除指定 Profile 的配置值。
     *
     * @param key 配置键。
     * @param profile Profile 名。
     * @return 删除结果。
     */
    public Map<String, Object> remove(String key, String profile) {
        return remove(key, true, profile);
    }

    /**
     * 删除当前 Profile 的配置值并控制是否重连渠道。
     *
     * @param key 配置键。
     * @param reconnectChannels 是否重连渠道。
     * @return 删除结果。
     */
    public Map<String, Object> remove(String key, boolean reconnectChannels) {
        return remove(key, reconnectChannels, null);
    }

    /** 删除指定 Profile 的配置值并控制当前 JVM 刷新。 */
    private Map<String, Object> remove(String key, boolean reconnectChannels, String profile) {
        catalog.require(key);
        store.remove(key, reconnectChannels, profile);
        return Collections.<String, Object>singletonMap("ok", true);
    }

    /**
     * 脱敏文本中的密钥、令牌和敏感路径。
     *
     * @param value 待脱敏值。
     * @return 首尾保留后的脱敏文本。
     */
    private String redact(String value) {
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
