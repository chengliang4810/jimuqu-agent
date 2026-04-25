package com.jimuqu.agent.gateway.service;

import com.jimuqu.agent.config.AppConfig;
import org.noear.solon.Solon;
import org.noear.solon.core.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * 运行时配置刷新服务。
 */
public class GatewayRuntimeRefreshService {
    private static final Logger log = LoggerFactory.getLogger(GatewayRuntimeRefreshService.class);

    private final AppConfig appConfig;
    private final ChannelConnectionManager channelConnectionManager;
    private volatile long lastConfigMtime;

    public GatewayRuntimeRefreshService(AppConfig appConfig,
                                        ChannelConnectionManager channelConnectionManager) {
        this.appConfig = appConfig;
        this.channelConnectionManager = channelConnectionManager;
        this.lastConfigMtime = fileMtime(appConfig.getRuntime().getConfigFile());
    }

    public void refreshIfNeeded() {
        long configMtime = fileMtime(appConfig.getRuntime().getConfigFile());
        if (configMtime == lastConfigMtime) {
            return;
        }
        refreshNow();
    }

    public synchronized void refreshNow() {
        refreshInternal(true);
    }

    public synchronized void refreshConfigOnly() {
        refreshInternal(false);
    }

    private void refreshInternal(boolean reconnectChannels) {
        AppConfig latest;
        try {
            if (Solon.cfg() == null) {
                Props props = new Props();
                props.put("jimuqu.runtime.home", appConfig.getRuntime().getHome());
                latest = AppConfig.load(props);
            } else {
                latest = AppConfig.load(Solon.cfg());
            }
        } catch (Throwable e) {
            log.debug("Skip runtime refresh because config reload failed", e);
            return;
        }
        appConfig.applyFrom(latest);
        lastConfigMtime = fileMtime(appConfig.getRuntime().getConfigFile());
        if (!reconnectChannels) {
            return;
        }
        channelConnectionManager.refreshAll();
    }

    private long fileMtime(String path) {
        if (path == null) {
            return 0L;
        }
        File file = new File(path);
        return file.exists() ? file.lastModified() : 0L;
    }
}
