package com.jimuqu.solon.claw.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.config.RuntimeConfigResolver;
import com.jimuqu.solon.claw.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.solon.claw.profile.ProfileMutationLock;
import com.jimuqu.solon.claw.web.profile.DashboardProfileConfigFile;
import com.jimuqu.solon.claw.web.profile.DashboardProfileContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 管理 Dashboard 工作区配置的 Profile、文件锁和运行时刷新。 */
final class DashboardRuntimeConfigStore {
    /** 当前 JVM Profile 配置，用于解析共享变更锁。 */
    private final AppConfig appConfig;

    /** 当前 JVM 工作区配置解析器。 */
    private final RuntimeConfigResolver configResolver;

    /** 当前 JVM 网关运行配置刷新服务。 */
    private final GatewayRuntimeRefreshService gatewayRuntimeRefreshService;

    /** 解析 Dashboard 显式选择的 Profile；为空时保留当前构造行为。 */
    private final DashboardProfileContext profileContext;

    /**
     * 创建工作区配置存储服务。
     *
     * @param appConfig 当前 JVM 配置。
     * @param gatewayRuntimeRefreshService 当前 JVM 网关刷新服务。
     * @param profileContext Dashboard Profile 请求上下文。
     */
    DashboardRuntimeConfigStore(
            AppConfig appConfig,
            GatewayRuntimeRefreshService gatewayRuntimeRefreshService,
            DashboardProfileContext profileContext) {
        this.appConfig = appConfig;
        this.configResolver = RuntimeConfigResolver.initialize(appConfig.getRuntime().getHome());
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
        this.profileContext = profileContext;
    }

    /**
     * 读取指定 Profile 的全部配置值。
     *
     * @param keys 按目录顺序排列的配置键。
     * @param profile Profile 名。
     * @return 保持输入顺序的配置值。
     */
    Map<String, String> readAll(List<String> keys, String profile) {
        return resolverFor(detachedScope(profile)).effectiveValues(keys);
    }

    /**
     * 读取指定 Profile 的单个配置值。
     *
     * @param key 配置键。
     * @param profile Profile 名。
     * @return 配置值；未设置时返回 null。
     */
    String read(String key, String profile) {
        return resolverFor(detachedScope(profile)).get(key);
    }

    /**
     * 写入指定 Profile 的配置值。
     *
     * @param key 配置键。
     * @param value 配置值。
     * @param reconnectChannels 是否重连当前 JVM 渠道。
     * @param profile Profile 名。
     */
    void write(String key, String value, boolean reconnectChannels, String profile) {
        DashboardProfileContext.Scope scope = detachedScope(profile);
        RuntimeConfigResolver resolver = resolverFor(scope);
        withMutationLock(
                scope == null ? appConfig : scope.getConfig(),
                () -> {
                    synchronized (
                            DashboardProfileConfigFile.lockFor(resolver.configFile().toPath())) {
                        resolver.setFileValue(key, value);
                    }
                    return null;
                });
        refreshCurrentProfile(scope, reconnectChannels);
    }

    /**
     * 删除指定 Profile 的配置值。
     *
     * @param key 配置键。
     * @param reconnectChannels 是否重连当前 JVM 渠道。
     * @param profile Profile 名。
     */
    void remove(String key, boolean reconnectChannels, String profile) {
        DashboardProfileContext.Scope scope = detachedScope(profile);
        RuntimeConfigResolver resolver = resolverFor(scope);
        withMutationLock(
                scope == null ? appConfig : scope.getConfig(),
                () -> {
                    synchronized (
                            DashboardProfileConfigFile.lockFor(resolver.configFile().toPath())) {
                        resolver.removeFileValue(key);
                    }
                    return null;
                });
        refreshCurrentProfile(scope, reconnectChannels);
    }

    /**
     * 解析审计事件应记录的实际 Profile 名。
     *
     * @param profile 请求指定的 Profile 名。
     * @return 已校验并规范化的实际 Profile 名。
     */
    String resolveProfileName(String profile) {
        if (profileContext != null) {
            return profileContext.resolve(profile).getName();
        }
        String requested = StrUtil.nullToEmpty(profile).trim();
        if (requested.length() == 0 || "current".equalsIgnoreCase(requested)) {
            return StrUtil.blankToDefault(System.getProperty("solonclaw.profile.name"), "default")
                    .trim()
                    .toLowerCase(Locale.ROOT);
        }
        return requested.toLowerCase(Locale.ROOT);
    }

    /** 当前 Profile 写入后按请求矩阵刷新配置，并仅在需要时重连渠道。 */
    private void refreshCurrentProfile(
            DashboardProfileContext.Scope scope, boolean reconnectChannels) {
        if (scope != null) {
            return;
        }
        if (reconnectChannels) {
            gatewayRuntimeRefreshService.refreshNow();
        } else {
            gatewayRuntimeRefreshService.refreshConfigOnly();
        }
    }

    /**
     * 在目标 Profile 所属根目录的跨进程锁内执行单键配置变更。
     *
     * @param config 目标 Profile 配置。
     * @param action 配置变更动作。
     * @param <T> 返回值类型。
     * @return 配置变更结果。
     */
    private <T> T withMutationLock(AppConfig config, ProfileMutationLock.Action<T> action) {
        try {
            return new ProfileMutationLock(config).withLock(action);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to lock runtime configuration update.", e);
        }
    }

    /**
     * 只在请求明确选择非当前 Profile 时返回独立 Scope。
     *
     * @param profile Profile 名。
     * @return 非当前 Profile Scope；当前 Profile 或未启用 Profile 上下文时返回 null。
     */
    private DashboardProfileContext.Scope detachedScope(String profile) {
        if (profileContext == null) {
            return null;
        }
        DashboardProfileContext.Scope scope = profileContext.resolve(profile);
        return scope.isCurrent() ? null : scope;
    }

    /**
     * 返回当前或指定 Profile 的配置解析器。
     *
     * @param scope 非当前 Profile Scope。
     * @return 对应工作区配置解析器。
     */
    private RuntimeConfigResolver resolverFor(DashboardProfileContext.Scope scope) {
        return scope == null
                ? configResolver
                : RuntimeConfigResolver.open(scope.getHome().toString());
    }
}
