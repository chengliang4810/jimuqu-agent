package com.jimuqu.agent.web;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.ChannelStatus;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.DeliveryService;

import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Dashboard 状态聚合服务。
 */
public class DashboardStatusService {
    private final AppConfig appConfig;
    private final SessionRepository sessionRepository;
    private final DeliveryService deliveryService;
    private final com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService;

    public DashboardStatusService(AppConfig appConfig,
                                  SessionRepository sessionRepository,
                                  DeliveryService deliveryService,
                                  com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        this.appConfig = appConfig;
        this.sessionRepository = sessionRepository;
        this.deliveryService = deliveryService;
        this.gatewayRuntimeRefreshService = gatewayRuntimeRefreshService;
    }

    public Map<String, Object> getStatus() throws Exception {
        gatewayRuntimeRefreshService.refreshIfNeeded();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<SessionRecord> recentSessions = sessionRepository.listRecent(20);
        List<ChannelStatus> statuses = deliveryService.statuses();
        int activeSessions = 0;
        long activeCutoff = System.currentTimeMillis() - 5L * 60L * 1000L;
        for (SessionRecord record : recentSessions) {
            if (record.getUpdatedAt() >= activeCutoff) {
                activeSessions++;
            }
        }

        boolean anyEnabled = false;
        boolean anyConnected = false;
        boolean anyFatal = false;
        Map<String, Object> platformStates = new LinkedHashMap<String, Object>();
        for (ChannelStatus status : statuses) {
            if (status.isEnabled()) {
                anyEnabled = true;
            }
            if (status.isConnected()) {
                anyConnected = true;
            }
            String detail = StrUtil.nullToEmpty(status.getDetail());
            boolean fatal = status.isEnabled() && !status.isConnected()
                    && (StrUtil.isNotBlank(status.getLastErrorCode()) || StrUtil.isNotBlank(status.getLastErrorMessage()));
            if (fatal) {
                anyFatal = true;
            }

            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("state", status.isEnabled()
                    ? (status.isConnected() ? "connected" : (fatal ? "fatal" : "disconnected"))
                    : "disabled");
            item.put("updated_at", isoNow());
            item.put("detail", detail);
            item.put("setup_state", status.getSetupState());
            item.put("connection_mode", status.getConnectionMode());
            item.put("missing_env", status.getMissingEnv());
            item.put("features", status.getFeatures());
            item.put("error_message", fatal ? StrUtil.blankToDefault(status.getLastErrorMessage(), detail) : null);
            item.put("error_code", fatal ? StrUtil.blankToDefault(status.getLastErrorCode(), "channel_unavailable") : null);
            platformStates.put(status.getPlatform().name().toLowerCase(), item);
        }

        String gatewayState;
        if (anyConnected) {
            gatewayState = "running";
        } else if (anyFatal) {
            gatewayState = "startup_failed";
        } else {
            gatewayState = anyEnabled ? "starting" : "stopped";
        }

        result.put("active_sessions", activeSessions);
        result.put("config_path", appConfig.getRuntime().getConfigOverrideFile());
        result.put("config_version", configVersion());
        result.put("env_path", appConfig.getRuntime().getEnvFile());
        result.put("gateway_exit_reason", anyFatal ? firstFatalDetail(statuses) : null);
        result.put("gateway_pid", parsePid());
        result.put("gateway_platforms", platformStates);
        result.put("gateway_running", anyConnected);
        result.put("gateway_state", gatewayState);
        result.put("gateway_updated_at", isoNow());
        result.put("hermes_home", appConfig.getRuntime().getHome());
        result.put("latest_config_version", configVersion());
        result.put("release_date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        result.put("version", resolveVersion());
        return result;
    }

    public Map<String, Object> getModelInfo() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("model", appConfig.getLlm().getModel());
        result.put("provider", appConfig.getLlm().getProvider());
        result.put("auto_context_length", appConfig.getLlm().getContextWindowTokens());
        result.put("config_context_length", appConfig.getLlm().getContextWindowTokens());
        result.put("effective_context_length", appConfig.getLlm().getContextWindowTokens());

        Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("supports_tools", true);
        capabilities.put("supports_vision", false);
        capabilities.put("supports_reasoning", true);
        capabilities.put("context_window", appConfig.getLlm().getContextWindowTokens());
        capabilities.put("max_output_tokens", appConfig.getLlm().getMaxTokens());
        capabilities.put("model_family", appConfig.getLlm().getProvider());
        result.put("capabilities", capabilities);
        return result;
    }

    private Integer parsePid() {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            int index = runtimeName.indexOf('@');
            String pid = index > 0 ? runtimeName.substring(0, index) : runtimeName;
            return Integer.valueOf(pid);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstFatalDetail(List<ChannelStatus> statuses) {
        for (ChannelStatus status : statuses) {
            if (status.isEnabled() && !status.isConnected()
                    && (StrUtil.isNotBlank(status.getLastErrorCode()) || StrUtil.isNotBlank(status.getLastErrorMessage()))) {
                return StrUtil.blankToDefault(status.getLastErrorMessage(), status.getDetail());
            }
        }
        return null;
    }

    private int configVersion() {
        java.io.File file = new java.io.File(appConfig.getRuntime().getConfigOverrideFile());
        if (!file.exists()) {
            return 0;
        }
        return (int) (file.lastModified() / 1000L);
    }

    private String resolveVersion() {
        Package pkg = getClass().getPackage();
        if (pkg != null && StrUtil.isNotBlank(pkg.getImplementationVersion())) {
            return pkg.getImplementationVersion();
        }
        return "0.0.1";
    }

    private String isoNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }
}
