package com.jimuqu.agent.gateway.service;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.service.ChannelAdapter;
import org.noear.solon.Solon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

/**
 * 运行时配置刷新服务。
 */
public class GatewayRuntimeRefreshService {
    private static final Logger log = LoggerFactory.getLogger(GatewayRuntimeRefreshService.class);

    private final AppConfig appConfig;
    private final Map<PlatformType, ChannelAdapter> channelAdapters;
    private volatile long lastEnvMtime;
    private volatile long lastConfigMtime;

    public GatewayRuntimeRefreshService(AppConfig appConfig,
                                        Map<PlatformType, ChannelAdapter> channelAdapters) {
        this.appConfig = appConfig;
        this.channelAdapters = channelAdapters;
        this.lastEnvMtime = fileMtime(appConfig.getRuntime().getEnvFile());
        this.lastConfigMtime = fileMtime(appConfig.getRuntime().getConfigOverrideFile());
    }

    public void refreshIfNeeded() {
        long envMtime = fileMtime(appConfig.getRuntime().getEnvFile());
        long configMtime = fileMtime(appConfig.getRuntime().getConfigOverrideFile());
        if (envMtime == lastEnvMtime && configMtime == lastConfigMtime) {
            return;
        }
        refreshNow();
    }

    public synchronized void refreshNow() {
        AppConfig latest;
        try {
            if (Solon.cfg() == null) {
                return;
            }
            latest = AppConfig.load(Solon.cfg());
        } catch (Throwable e) {
            log.debug("Skip runtime refresh because Solon config is unavailable", e);
            return;
        }
        appConfig.applyFrom(latest);
        lastEnvMtime = fileMtime(appConfig.getRuntime().getEnvFile());
        lastConfigMtime = fileMtime(appConfig.getRuntime().getConfigOverrideFile());
        for (ChannelAdapter adapter : channelAdapters.values()) {
            try {
                adapter.disconnect();
            } catch (Exception e) {
                log.debug("Channel disconnect during refresh failed: {}", adapter.platform(), e);
            }
            try {
                boolean connected = adapter.connect();
                log.info("[CHANNEL-REFRESH] platform={}, enabled={}, connected={}, detail={}",
                        adapter.platform(),
                        adapter.isEnabled(),
                        connected,
                        adapter.detail());
            } catch (Exception e) {
                log.warn("[CHANNEL-REFRESH] reconnect failed: platform={}, message={}",
                        adapter.platform(),
                        e.getMessage());
            }
        }
    }

    private long fileMtime(String path) {
        if (path == null) {
            return 0L;
        }
        File file = new File(path);
        return file.exists() ? file.lastModified() : 0L;
    }
}
