package com.jimuqu.agent.config;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.support.constants.GatewayBehaviorConstants;
import com.jimuqu.agent.support.constants.RuntimePathConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.noear.solon.core.Props;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 应用级配置对象，负责承接 Solon 配置并做环境变量覆盖与路径标准化。
 */
@Getter
@Setter
@NoArgsConstructor
public class AppConfig {
    /**
     * 运行时目录配置。
     */
    private RuntimeConfig runtime = new RuntimeConfig();

    /**
     * 大模型接入配置。
     */
    private LlmConfig llm = new LlmConfig();

    /**
     * 定时任务调度配置。
     */
    private SchedulerConfig scheduler = new SchedulerConfig();

    /**
     * 各渠道接入配置。
     */
    private ChannelsConfig channels = new ChannelsConfig();

    /**
     * 网关通用授权配置。
     */
    private GatewayConfig gateway = new GatewayConfig();

    /**
     * 从 Solon Props 构建应用配置。
     *
     * @param props Solon 启动时加载的配置源
     * @return 标准化后的配置对象
     */
    public static AppConfig load(Props props) {
        AppConfig config = new AppConfig();

        config.getRuntime().setHome(props.get("jimuqu.runtime.home", RuntimePathConstants.RUNTIME_HOME));
        config.getRuntime().setContextDir(props.get("jimuqu.runtime.contextDir", RuntimePathConstants.CONTEXT_DIR));
        config.getRuntime().setSkillsDir(props.get("jimuqu.runtime.skillsDir", RuntimePathConstants.SKILLS_DIR));
        config.getRuntime().setCacheDir(props.get("jimuqu.runtime.cacheDir", RuntimePathConstants.CACHE_DIR));
        config.getRuntime().setStateDb(props.get("jimuqu.runtime.stateDb", RuntimePathConstants.STATE_DB));

        config.getLlm().setProvider(props.get("jimuqu.llm.provider", RuntimePathConstants.DEFAULT_LLM_PROVIDER));
        config.getLlm().setApiUrl(props.get("jimuqu.llm.apiUrl", RuntimePathConstants.DEFAULT_LLM_API_URL));
        config.getLlm().setApiKey(resolveSecret("JIMUQU_LLM_API_KEY", props.get("jimuqu.llm.apiKey", "")));
        config.getLlm().setModel(props.get("jimuqu.llm.model", RuntimePathConstants.DEFAULT_LLM_MODEL));
        config.getLlm().setStream(props.getBool("jimuqu.llm.stream", false));
        config.getLlm().setReasoningEffort(props.get("jimuqu.llm.reasoningEffort", RuntimePathConstants.DEFAULT_REASONING_EFFORT));
        config.getLlm().setTemperature(props.getDouble("jimuqu.llm.temperature", RuntimePathConstants.DEFAULT_TEMPERATURE));
        config.getLlm().setMaxTokens(props.getInt("jimuqu.llm.maxTokens", RuntimePathConstants.DEFAULT_MAX_TOKENS));

        config.getScheduler().setEnabled(props.getBool("jimuqu.scheduler.enabled", true));
        config.getScheduler().setTickSeconds(props.getInt("jimuqu.scheduler.tickSeconds", RuntimePathConstants.DEFAULT_SCHEDULER_TICK_SECONDS));

        applyChannelConfig(
                config.getChannels().getFeishu(),
                props,
                "feishu",
                "JIMUQU_FEISHU_ALLOWED_USERS",
                "JIMUQU_FEISHU_ALLOW_ALL_USERS",
                "JIMUQU_FEISHU_UNAUTHORIZED_DM_BEHAVIOR"
        );
        config.getChannels().getFeishu().setEnabled(props.getBool("jimuqu.channels.feishu.enabled", false));
        config.getChannels().getFeishu().setAppId(resolveSecret("JIMUQU_FEISHU_APP_ID", props.get("jimuqu.channels.feishu.appId", "")));
        config.getChannels().getFeishu().setAppSecret(resolveSecret("JIMUQU_FEISHU_APP_SECRET", props.get("jimuqu.channels.feishu.appSecret", "")));
        config.getChannels().getFeishu().setWebsocketUrl(props.get("jimuqu.channels.feishu.websocketUrl", ""));

        applyChannelConfig(
                config.getChannels().getDingtalk(),
                props,
                "dingtalk",
                "JIMUQU_DINGTALK_ALLOWED_USERS",
                "JIMUQU_DINGTALK_ALLOW_ALL_USERS",
                "JIMUQU_DINGTALK_UNAUTHORIZED_DM_BEHAVIOR"
        );
        config.getChannels().getDingtalk().setEnabled(resolveBoolean("JIMUQU_DINGTALK_ENABLED", props.getBool("jimuqu.channels.dingtalk.enabled", false)));
        config.getChannels().getDingtalk().setClientId(resolveSecret("JIMUQU_DINGTALK_CLIENT_ID", props.get("jimuqu.channels.dingtalk.clientId", "")));
        config.getChannels().getDingtalk().setClientSecret(resolveSecret("JIMUQU_DINGTALK_CLIENT_SECRET", props.get("jimuqu.channels.dingtalk.clientSecret", "")));
        config.getChannels().getDingtalk().setRobotCode(resolveSecret("JIMUQU_DINGTALK_ROBOT_CODE", props.get("jimuqu.channels.dingtalk.robotCode", "")));
        config.getChannels().getDingtalk().setCoolAppCode(props.get("jimuqu.channels.dingtalk.coolAppCode", ""));
        config.getChannels().getDingtalk().setStreamUrl(props.get("jimuqu.channels.dingtalk.streamUrl", ""));

        applyChannelConfig(
                config.getChannels().getWecom(),
                props,
                "wecom",
                "JIMUQU_WECOM_ALLOWED_USERS",
                "JIMUQU_WECOM_ALLOW_ALL_USERS",
                "JIMUQU_WECOM_UNAUTHORIZED_DM_BEHAVIOR"
        );
        config.getChannels().getWecom().setEnabled(props.getBool("jimuqu.channels.wecom.enabled", false));
        config.getChannels().getWecom().setBotId(resolveSecret("JIMUQU_WECOM_BOT_ID", props.get("jimuqu.channels.wecom.botId", "")));
        config.getChannels().getWecom().setSecret(resolveSecret("JIMUQU_WECOM_SECRET", props.get("jimuqu.channels.wecom.secret", "")));
        config.getChannels().getWecom().setWebsocketUrl(props.get("jimuqu.channels.wecom.websocketUrl", ""));

        applyChannelConfig(
                config.getChannels().getWeixin(),
                props,
                "weixin",
                "JIMUQU_WEIXIN_ALLOWED_USERS",
                "JIMUQU_WEIXIN_ALLOW_ALL_USERS",
                "JIMUQU_WEIXIN_UNAUTHORIZED_DM_BEHAVIOR"
        );
        config.getChannels().getWeixin().setEnabled(props.getBool("jimuqu.channels.weixin.enabled", false));
        config.getChannels().getWeixin().setToken(resolveSecret("JIMUQU_WEIXIN_TOKEN", props.get("jimuqu.channels.weixin.token", "")));
        config.getChannels().getWeixin().setLongPollUrl(props.get("jimuqu.channels.weixin.longPollUrl", ""));

        config.getGateway().setAllowedUsers(resolveList("JIMUQU_GATEWAY_ALLOWED_USERS", props.get("jimuqu.gateway.allowedUsers", "")));
        config.getGateway().setAllowAllUsers(resolveBoolean("JIMUQU_GATEWAY_ALLOW_ALL_USERS", props.getBool("jimuqu.gateway.allowAllUsers", false)));

        config.normalizePaths();
        return config;
    }

    /**
     * 标准化运行时路径，确保所有路径都转换为基于 `user.dir` 的绝对路径。
     */
    public void normalizePaths() {
        File userDir = new File(System.getProperty("user.dir"));
        runtime.setHome(asAbsolute(new File(runtime.getHome()), userDir).getAbsolutePath());
        runtime.setContextDir(asAbsolute(new File(runtime.getContextDir()), userDir).getAbsolutePath());
        runtime.setSkillsDir(asAbsolute(new File(runtime.getSkillsDir()), userDir).getAbsolutePath());
        runtime.setCacheDir(asAbsolute(new File(runtime.getCacheDir()), userDir).getAbsolutePath());
        runtime.setStateDb(asAbsolute(new File(runtime.getStateDb()), userDir).getAbsolutePath());
    }

    /**
     * 批量装配渠道共性配置。
     *
     * @param channelConfig               目标渠道配置
     * @param props                       原始配置
     * @param channelName                 渠道名称
     * @param allowedUsersEnvName         允许名单环境变量名
     * @param allowAllUsersEnvName        全开放环境变量名
     * @param unauthorizedBehaviorEnvName 未授权私聊行为环境变量名
     */
    private static void applyChannelConfig(ChannelConfig channelConfig,
                                           Props props,
                                           String channelName,
                                           String allowedUsersEnvName,
                                           String allowAllUsersEnvName,
                                           String unauthorizedBehaviorEnvName) {
        channelConfig.setAllowedUsers(resolveList(allowedUsersEnvName, props.get("jimuqu.channels." + channelName + ".allowedUsers", "")));
        channelConfig.setAllowAllUsers(resolveBoolean(allowAllUsersEnvName, props.getBool("jimuqu.channels." + channelName + ".allowAllUsers", false)));
        channelConfig.setUnauthorizedDmBehavior(resolveBehavior(
                unauthorizedBehaviorEnvName,
                props.get(
                        "jimuqu.channels." + channelName + ".unauthorizedDmBehavior",
                        GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR
                )
        ));
    }

    /**
     * 优先从环境变量解析密钥。
     */
    private static String resolveSecret(String envName, String fallback) {
        String envValue = System.getenv(envName);
        if (StrUtil.isNotBlank(envValue)) {
            return envValue.trim();
        }
        return StrUtil.nullToEmpty(fallback).trim();
    }

    /**
     * 支持通过环境变量覆盖布尔配置。
     */
    private static boolean resolveBoolean(String envName, boolean fallback) {
        String envValue = System.getenv(envName);
        if (StrUtil.isBlank(envValue)) {
            return fallback;
        }
        String normalized = envValue.trim();
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    /**
     * 支持逗号分隔的用户列表解析。
     */
    private static List<String> resolveList(String envName, String fallback) {
        String envValue = System.getenv(envName);
        if (StrUtil.isNotBlank(envValue)) {
            return splitList(envValue);
        }
        return splitList(fallback);
    }

    /**
     * 统一收敛未授权私聊用户的处理行为。
     */
    private static String resolveBehavior(String envName, String fallback) {
        String envValue = System.getenv(envName);
        String value = StrUtil.isNotBlank(envValue)
                ? envValue.trim()
                : StrUtil.nullToDefault(fallback, GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR).trim();
        if (GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE.equalsIgnoreCase(value)) {
            return GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE;
        }
        return GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR;
    }

    /**
     * 将逗号分隔列表转为字符串集合。
     */
    private static List<String> splitList(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<String>();
        for (String item : Arrays.asList(raw.split(","))) {
            String trimmed = item == null ? "" : item.trim();
            if (trimmed.length() > 0) {
                values.add(trimmed);
            }
        }
        return values;
    }

    /**
     * 将相对路径转换为绝对路径。
     */
    private File asAbsolute(File file, File base) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(base, file.getPath());
    }

    /**
     * 运行时目录配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RuntimeConfig {
        /**
         * 运行时根目录。
         */
        private String home;

        /**
         * 上下文文件目录。
         */
        private String contextDir;

        /**
         * skills 本地目录。
         */
        private String skillsDir;

        /**
         * 缓存目录。
         */
        private String cacheDir;

        /**
         * SQLite 状态库路径。
         */
        private String stateDb;
    }

    /**
     * 大模型接入配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LlmConfig {
        /**
         * 协议提供方。
         */
        private String provider;

        /**
         * 请求地址。
         */
        private String apiUrl;

        /**
         * 访问密钥。
         */
        private String apiKey;

        /**
         * 默认模型名。
         */
        private String model;

        /**
         * 是否使用流式输出。
         */
        private boolean stream;

        /**
         * 推理强度。
         */
        private String reasoningEffort;

        /**
         * 温度参数。
         */
        private double temperature;

        /**
         * 最大输出 token。
         */
        private int maxTokens;
    }

    /**
     * 调度器配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SchedulerConfig {
        /**
         * 是否启用调度器。
         */
        private boolean enabled;

        /**
         * 调度轮询周期，单位秒。
         */
        private int tickSeconds;
    }

    /**
     * 全部渠道配置集合。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChannelsConfig {
        /**
         * 飞书渠道配置。
         */
        private ChannelConfig feishu = new ChannelConfig();

        /**
         * 钉钉渠道配置。
         */
        private ChannelConfig dingtalk = new ChannelConfig();

        /**
         * 企微渠道配置。
         */
        private ChannelConfig wecom = new ChannelConfig();

        /**
         * 微信渠道配置。
         */
        private ChannelConfig weixin = new ChannelConfig();
    }

    /**
     * 单个渠道配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ChannelConfig {
        /**
         * 是否启用该渠道。
         */
        private boolean enabled;

        /**
         * 飞书应用 ID。
         */
        private String appId;

        /**
         * 飞书应用密钥。
         */
        private String appSecret;

        /**
         * 钉钉客户端 ID。
         */
        private String clientId;

        /**
         * 钉钉客户端密钥。
         */
        private String clientSecret;

        /**
         * 企微机器人标识。
         */
        private String botId;

        /**
         * 企微密钥。
         */
        private String secret;

        /**
         * 微信令牌。
         */
        private String token;

        /**
         * 钉钉机器人编码。
         */
        private String robotCode;

        /**
         * 钉钉 Cool App 编码。
         */
        private String coolAppCode;

        /**
         * WebSocket 地址。
         */
        private String websocketUrl;

        /**
         * Stream 地址。
         */
        private String streamUrl;

        /**
         * Long Poll 地址。
         */
        private String longPollUrl;

        /**
         * 渠道允许名单。
         */
        private List<String> allowedUsers = new ArrayList<String>();

        /**
         * 是否允许该渠道所有用户访问。
         */
        private boolean allowAllUsers;

        /**
         * 未授权私聊行为。
         */
        private String unauthorizedDmBehavior = GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR;
    }

    /**
     * 网关通用授权配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class GatewayConfig {
        /**
         * 全局允许名单。
         */
        private List<String> allowedUsers = new ArrayList<String>();

        /**
         * 是否允许所有用户访问。
         */
        private boolean allowAllUsers;
    }
}
