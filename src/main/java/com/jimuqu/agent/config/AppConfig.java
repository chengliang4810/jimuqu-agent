package com.jimuqu.agent.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.support.constants.CheckpointConstants;
import com.jimuqu.agent.support.constants.CompressionConstants;
import com.jimuqu.agent.support.constants.GatewayBehaviorConstants;
import com.jimuqu.agent.support.constants.RuntimePathConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.noear.snack4.ONode;
import org.noear.solon.core.Props;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * 上下文压缩配置。
     */
    private CompressionConfig compression = new CompressionConfig();

    /**
     * 任务后自动学习配置。
     */
    private LearningConfig learning = new LearningConfig();

    /**
     * 文件快照与回滚配置。
     */
    private RollbackConfig rollback = new RollbackConfig();

    /**
     * 聊天窗口显示配置。
     */
    private DisplayConfig display = new DisplayConfig();

    /**
     * 各渠道接入配置。
     */
    private ChannelsConfig channels = new ChannelsConfig();

    /**
     * 网关通用授权配置。
     */
    private GatewayConfig gateway = new GatewayConfig();

    /**
     * Agent 运行配置。
     */
    private AgentConfig agent = new AgentConfig();

    /**
     * ReAct 运行配置。
     */
    private ReActConfig react = new ReActConfig();

    /**
     * 从 Solon Props 构建应用配置。
     *
     * @param props Solon 启动时加载的配置源
     * @return 标准化后的配置对象
     */
    public static AppConfig load(Props props) {
        AppConfig config = new AppConfig();
        File userDir = new File(System.getProperty("user.dir"));
        File runtimeHome = asAbsoluteStatic(
                new File(props.get("jimuqu.runtime.home", RuntimePathConstants.RUNTIME_HOME)),
                userDir
        );
        Map<String, Object> overrides = loadFlatOverrides(runtimeHome);
        RuntimeEnvResolver envResolver = RuntimeEnvResolver.initialize(runtimeHome.getAbsolutePath());

        config.getRuntime().setHome(resolveEnvString("JIMUQU_RUNTIME_HOME", readString(props, overrides, "jimuqu.runtime.home", RuntimePathConstants.RUNTIME_HOME)));
        config.getRuntime().setContextDir(resolveRuntimePath(
                resolveEnvString("JIMUQU_RUNTIME_CONTEXT_DIR", readString(props, overrides, "jimuqu.runtime.contextDir", RuntimePathConstants.CONTEXT_DIR)),
                config.getRuntime().getHome(),
                RuntimePathConstants.CONTEXT_DIR,
                "context"
        ));
        config.getRuntime().setSkillsDir(resolveRuntimePath(
                resolveEnvString("JIMUQU_RUNTIME_SKILLS_DIR", readString(props, overrides, "jimuqu.runtime.skillsDir", RuntimePathConstants.SKILLS_DIR)),
                config.getRuntime().getHome(),
                RuntimePathConstants.SKILLS_DIR,
                "skills"
        ));
        config.getRuntime().setCacheDir(resolveRuntimePath(
                resolveEnvString("JIMUQU_RUNTIME_CACHE_DIR", readString(props, overrides, "jimuqu.runtime.cacheDir", RuntimePathConstants.CACHE_DIR)),
                config.getRuntime().getHome(),
                RuntimePathConstants.CACHE_DIR,
                "cache"
        ));
        config.getRuntime().setStateDb(resolveRuntimePath(
                resolveEnvString("JIMUQU_RUNTIME_STATE_DB", readString(props, overrides, "jimuqu.runtime.stateDb", RuntimePathConstants.STATE_DB)),
                config.getRuntime().getHome(),
                RuntimePathConstants.STATE_DB,
                "state.db"
        ));
        config.getRuntime().setConfigOverrideFile(new File(runtimeHome, "config.override.yml").getPath());
        config.getRuntime().setEnvFile(envResolver.envFile().getPath());
        config.getRuntime().setLogsDir(new File(runtimeHome, "logs").getPath());

        config.getLlm().setProvider(resolveEnvString("JIMUQU_LLM_PROVIDER", readString(props, overrides, "jimuqu.llm.provider", RuntimePathConstants.DEFAULT_LLM_PROVIDER)));
        config.getLlm().setApiUrl(resolveEnvString("JIMUQU_LLM_API_URL", readString(props, overrides, "jimuqu.llm.apiUrl", RuntimePathConstants.DEFAULT_LLM_API_URL)));
        config.getLlm().setApiKey(resolveSecret("JIMUQU_LLM_API_KEY", props.get("jimuqu.llm.apiKey", "")));
        config.getLlm().setModel(resolveEnvString("JIMUQU_LLM_MODEL", readString(props, overrides, "jimuqu.llm.model", RuntimePathConstants.DEFAULT_LLM_MODEL)));
        config.getLlm().setStream(resolveBoolean("JIMUQU_LLM_STREAM", readBoolean(props, overrides, "jimuqu.llm.stream", false)));
        config.getLlm().setReasoningEffort(resolveEnvString("JIMUQU_LLM_REASONING_EFFORT", readString(props, overrides, "jimuqu.llm.reasoningEffort", RuntimePathConstants.DEFAULT_REASONING_EFFORT)));
        config.getLlm().setTemperature(resolveDouble("JIMUQU_LLM_TEMPERATURE", readDouble(props, overrides, "jimuqu.llm.temperature", RuntimePathConstants.DEFAULT_TEMPERATURE)));
        config.getLlm().setMaxTokens(resolveInt("JIMUQU_LLM_MAX_TOKENS", readInt(props, overrides, "jimuqu.llm.maxTokens", RuntimePathConstants.DEFAULT_MAX_TOKENS)));
        config.getLlm().setContextWindowTokens(resolveInt("JIMUQU_LLM_CONTEXT_WINDOW_TOKENS", readInt(props, overrides, "jimuqu.llm.contextWindowTokens", RuntimePathConstants.DEFAULT_CONTEXT_WINDOW_TOKENS)));

        config.getScheduler().setEnabled(resolveBoolean("JIMUQU_SCHEDULER_ENABLED", readBoolean(props, overrides, "jimuqu.scheduler.enabled", true)));
        config.getScheduler().setTickSeconds(resolveInt("JIMUQU_SCHEDULER_TICK_SECONDS", readInt(props, overrides, "jimuqu.scheduler.tickSeconds", RuntimePathConstants.DEFAULT_SCHEDULER_TICK_SECONDS)));

        config.getCompression().setEnabled(resolveBoolean("JIMUQU_COMPRESSION_ENABLED", readBoolean(props, overrides, "jimuqu.compression.enabled", true)));
        config.getCompression().setThresholdPercent(resolveDouble("JIMUQU_COMPRESSION_THRESHOLD_PERCENT", readDouble(props, overrides, "jimuqu.compression.thresholdPercent", CompressionConstants.DEFAULT_THRESHOLD_PERCENT)));
        config.getCompression().setSummaryModel(resolveEnvString("JIMUQU_COMPRESSION_SUMMARY_MODEL", readString(props, overrides, "jimuqu.compression.summaryModel", "")));
        config.getCompression().setProtectHeadMessages(resolveInt("JIMUQU_COMPRESSION_PROTECT_HEAD_MESSAGES", readInt(props, overrides, "jimuqu.compression.protectHeadMessages", CompressionConstants.DEFAULT_PROTECT_HEAD_MESSAGES)));
        config.getCompression().setTailRatio(resolveDouble("JIMUQU_COMPRESSION_TAIL_RATIO", readDouble(props, overrides, "jimuqu.compression.tailRatio", CompressionConstants.DEFAULT_TAIL_RATIO)));

        config.getLearning().setEnabled(resolveBoolean("JIMUQU_LEARNING_ENABLED", readBoolean(props, overrides, "jimuqu.learning.enabled", true)));
        config.getLearning().setToolCallThreshold(resolveInt("JIMUQU_LEARNING_TOOL_CALL_THRESHOLD", readInt(props, overrides, "jimuqu.learning.toolCallThreshold", 5)));

        config.getRollback().setEnabled(resolveBoolean("JIMUQU_ROLLBACK_ENABLED", readBoolean(props, overrides, "jimuqu.rollback.enabled", true)));
        config.getRollback().setMaxCheckpointsPerSource(resolveInt("JIMUQU_ROLLBACK_MAX_CHECKPOINTS_PER_SOURCE", readInt(props, overrides, "jimuqu.rollback.maxCheckpointsPerSource", CheckpointConstants.DEFAULT_MAX_CHECKPOINTS_PER_SOURCE)));

        config.getDisplay().setToolProgress(resolveEnvString("JIMUQU_DISPLAY_TOOL_PROGRESS", readString(props, overrides, "jimuqu.display.toolProgress", "off")));
        config.getDisplay().setShowReasoning(resolveBoolean("JIMUQU_DISPLAY_SHOW_REASONING", readBoolean(props, overrides, "jimuqu.display.showReasoning", false)));
        config.getDisplay().setToolPreviewLength(resolveInt("JIMUQU_DISPLAY_TOOL_PREVIEW_LENGTH", readInt(props, overrides, "jimuqu.display.toolPreviewLength", 80)));
        config.getDisplay().setProgressThrottleMs(resolveInt("JIMUQU_DISPLAY_PROGRESS_THROTTLE_MS", readInt(props, overrides, "jimuqu.display.progressThrottleMs", 1500)));

        applyChannelConfig(
                config.getChannels().getFeishu(),
                props,
                overrides,
                "feishu",
                "JIMUQU_FEISHU_ALLOWED_USERS",
                "JIMUQU_FEISHU_ALLOW_ALL_USERS",
                "JIMUQU_FEISHU_UNAUTHORIZED_DM_BEHAVIOR",
                "JIMUQU_FEISHU_DM_POLICY",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                "JIMUQU_FEISHU_GROUP_POLICY",
                GatewayBehaviorConstants.GROUP_POLICY_ALLOWLIST,
                "JIMUQU_FEISHU_GROUP_ALLOWED_USERS"
        );
        config.getChannels().getFeishu().setEnabled(resolveBoolean("JIMUQU_FEISHU_ENABLED", readBoolean(props, overrides, "jimuqu.channels.feishu.enabled", false)));
        config.getChannels().getFeishu().setAppId(resolveSecret("JIMUQU_FEISHU_APP_ID", props.get("jimuqu.channels.feishu.appId", "")));
        config.getChannels().getFeishu().setAppSecret(resolveSecret("JIMUQU_FEISHU_APP_SECRET", props.get("jimuqu.channels.feishu.appSecret", "")));
        config.getChannels().getFeishu().setWebsocketUrl(resolveEnvString("JIMUQU_FEISHU_WEBSOCKET_URL", readString(props, overrides, "jimuqu.channels.feishu.websocketUrl", "")));
        config.getChannels().getFeishu().setBotOpenId(resolveSecret("JIMUQU_FEISHU_BOT_OPEN_ID", props.get("jimuqu.channels.feishu.botOpenId", "")));
        config.getChannels().getFeishu().setBotUserId(resolveSecret("JIMUQU_FEISHU_BOT_USER_ID", props.get("jimuqu.channels.feishu.botUserId", "")));
        config.getChannels().getFeishu().setBotName(resolveEnvString("JIMUQU_FEISHU_BOT_NAME", readString(props, overrides, "jimuqu.channels.feishu.botName", "")));
        config.getChannels().getFeishu().setToolProgress(resolveEnvString("JIMUQU_FEISHU_TOOL_PROGRESS", readString(props, overrides, "jimuqu.channels.feishu.toolProgress", "new")));

        applyChannelConfig(
                config.getChannels().getDingtalk(),
                props,
                overrides,
                "dingtalk",
                "JIMUQU_DINGTALK_ALLOWED_USERS",
                "JIMUQU_DINGTALK_ALLOW_ALL_USERS",
                "JIMUQU_DINGTALK_UNAUTHORIZED_DM_BEHAVIOR",
                "JIMUQU_DINGTALK_DM_POLICY",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                "JIMUQU_DINGTALK_GROUP_POLICY",
                GatewayBehaviorConstants.GROUP_POLICY_OPEN,
                "JIMUQU_DINGTALK_GROUP_ALLOWED_USERS"
        );
        config.getChannels().getDingtalk().setEnabled(resolveBoolean("JIMUQU_DINGTALK_ENABLED", readBoolean(props, overrides, "jimuqu.channels.dingtalk.enabled", false)));
        config.getChannels().getDingtalk().setClientId(resolveSecret("JIMUQU_DINGTALK_CLIENT_ID", props.get("jimuqu.channels.dingtalk.clientId", "")));
        config.getChannels().getDingtalk().setClientSecret(resolveSecret("JIMUQU_DINGTALK_CLIENT_SECRET", props.get("jimuqu.channels.dingtalk.clientSecret", "")));
        config.getChannels().getDingtalk().setRobotCode(resolveSecret("JIMUQU_DINGTALK_ROBOT_CODE", props.get("jimuqu.channels.dingtalk.robotCode", "")));
        config.getChannels().getDingtalk().setCoolAppCode(resolveEnvString("JIMUQU_DINGTALK_COOL_APP_CODE", readString(props, overrides, "jimuqu.channels.dingtalk.coolAppCode", "")));
        config.getChannels().getDingtalk().setStreamUrl(resolveEnvString("JIMUQU_DINGTALK_STREAM_URL", readString(props, overrides, "jimuqu.channels.dingtalk.streamUrl", "")));
        config.getChannels().getDingtalk().setToolProgress(resolveEnvString("JIMUQU_DINGTALK_TOOL_PROGRESS", readString(props, overrides, "jimuqu.channels.dingtalk.toolProgress", "new")));
        config.getChannels().getDingtalk().setProgressCardTemplateId(resolveEnvString("JIMUQU_DINGTALK_PROGRESS_CARD_TEMPLATE_ID", readString(props, overrides, "jimuqu.channels.dingtalk.progressCardTemplateId", "")));

        applyChannelConfig(
                config.getChannels().getWecom(),
                props,
                overrides,
                "wecom",
                "JIMUQU_WECOM_ALLOWED_USERS",
                "JIMUQU_WECOM_ALLOW_ALL_USERS",
                "JIMUQU_WECOM_UNAUTHORIZED_DM_BEHAVIOR",
                "JIMUQU_WECOM_DM_POLICY",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                "JIMUQU_WECOM_GROUP_POLICY",
                GatewayBehaviorConstants.GROUP_POLICY_OPEN,
                "JIMUQU_WECOM_GROUP_ALLOWED_USERS"
        );
        config.getChannels().getWecom().setEnabled(resolveBoolean("JIMUQU_WECOM_ENABLED", readBoolean(props, overrides, "jimuqu.channels.wecom.enabled", false)));
        config.getChannels().getWecom().setBotId(resolveSecret("JIMUQU_WECOM_BOT_ID", props.get("jimuqu.channels.wecom.botId", "")));
        config.getChannels().getWecom().setSecret(resolveSecret("JIMUQU_WECOM_SECRET", props.get("jimuqu.channels.wecom.secret", "")));
        config.getChannels().getWecom().setWebsocketUrl(resolveEnvString("JIMUQU_WECOM_WEBSOCKET_URL", readString(props, overrides, "jimuqu.channels.wecom.websocketUrl", "")));
        config.getChannels().getWecom().setToolProgress(resolveEnvString("JIMUQU_WECOM_TOOL_PROGRESS", readString(props, overrides, "jimuqu.channels.wecom.toolProgress", "off")));
        config.getChannels().getWecom().setGroupMemberAllowedUsers(
                collectGroupAllowMap(props, overrides, "jimuqu.channels.wecom.groups.", "JIMUQU_WECOM_GROUP_MEMBER_ALLOW_MAP_JSON")
        );

        applyChannelConfig(
                config.getChannels().getWeixin(),
                props,
                overrides,
                "weixin",
                "JIMUQU_WEIXIN_ALLOWED_USERS",
                "JIMUQU_WEIXIN_ALLOW_ALL_USERS",
                "JIMUQU_WEIXIN_UNAUTHORIZED_DM_BEHAVIOR",
                "JIMUQU_WEIXIN_DM_POLICY",
                GatewayBehaviorConstants.DM_POLICY_OPEN,
                "JIMUQU_WEIXIN_GROUP_POLICY",
                GatewayBehaviorConstants.GROUP_POLICY_DISABLED,
                "JIMUQU_WEIXIN_GROUP_ALLOWED_USERS"
        );
        config.getChannels().getWeixin().setEnabled(resolveBoolean("JIMUQU_WEIXIN_ENABLED", readBoolean(props, overrides, "jimuqu.channels.weixin.enabled", false)));
        config.getChannels().getWeixin().setToken(resolveSecret("JIMUQU_WEIXIN_TOKEN", props.get("jimuqu.channels.weixin.token", "")));
        config.getChannels().getWeixin().setAccountId(resolveSecret("JIMUQU_WEIXIN_ACCOUNT_ID", props.get("jimuqu.channels.weixin.accountId", "")));
        config.getChannels().getWeixin().setBaseUrl(resolveEnvString("JIMUQU_WEIXIN_BASE_URL", readString(props, overrides, "jimuqu.channels.weixin.baseUrl", "")));
        config.getChannels().getWeixin().setCdnBaseUrl(resolveEnvString("JIMUQU_WEIXIN_CDN_BASE_URL", readString(props, overrides, "jimuqu.channels.weixin.cdnBaseUrl", "")));
        config.getChannels().getWeixin().setLongPollUrl(resolveEnvString("JIMUQU_WEIXIN_LONG_POLL_URL", readString(props, overrides, "jimuqu.channels.weixin.longPollUrl", "")));
        config.getChannels().getWeixin().setSplitMultilineMessages(resolveBoolean("JIMUQU_WEIXIN_SPLIT_MULTILINE_MESSAGES", readBoolean(props, overrides, "jimuqu.channels.weixin.splitMultilineMessages", false)));
        config.getChannels().getWeixin().setSendChunkDelaySeconds(resolveDouble("JIMUQU_WEIXIN_SEND_CHUNK_DELAY_SECONDS", readDouble(props, overrides, "jimuqu.channels.weixin.sendChunkDelaySeconds", 0.35D)));
        config.getChannels().getWeixin().setSendChunkRetries(resolveInt("JIMUQU_WEIXIN_SEND_CHUNK_RETRIES", readInt(props, overrides, "jimuqu.channels.weixin.sendChunkRetries", 2)));
        config.getChannels().getWeixin().setSendChunkRetryDelaySeconds(resolveDouble("JIMUQU_WEIXIN_SEND_CHUNK_RETRY_DELAY_SECONDS", readDouble(props, overrides, "jimuqu.channels.weixin.sendChunkRetryDelaySeconds", 1.0D)));
        config.getChannels().getWeixin().setToolProgress(resolveEnvString("JIMUQU_WEIXIN_TOOL_PROGRESS", readString(props, overrides, "jimuqu.channels.weixin.toolProgress", "off")));

        config.getGateway().setAllowedUsers(resolveList("JIMUQU_GATEWAY_ALLOWED_USERS", readRaw(props, overrides, "jimuqu.gateway.allowedUsers", "")));
        config.getGateway().setAllowAllUsers(resolveBoolean("JIMUQU_GATEWAY_ALLOW_ALL_USERS", readBoolean(props, overrides, "jimuqu.gateway.allowAllUsers", false)));
        config.getAgent().setPersonalities(loadPersonalities(props, overrides));
        config.getAgent().getHeartbeat().setEnabled(resolveBoolean("JIMUQU_AGENT_HEARTBEAT_ENABLED", readBoolean(props, overrides, "jimuqu.agent.heartbeat.enabled", false)));
        config.getAgent().getHeartbeat().setIntervalMinutes(resolveInt("JIMUQU_AGENT_HEARTBEAT_INTERVAL_MINUTES", readInt(props, overrides, "jimuqu.agent.heartbeat.intervalMinutes", RuntimePathConstants.DEFAULT_HEARTBEAT_INTERVAL_MINUTES)));
        config.getAgent().getHeartbeat().setDeliveryMode(resolveEnvString("JIMUQU_AGENT_HEARTBEAT_DELIVERY_MODE", readString(props, overrides, "jimuqu.agent.heartbeat.deliveryMode", RuntimePathConstants.DEFAULT_HEARTBEAT_DELIVERY_MODE)));
        config.getAgent().getHeartbeat().setQuietToken(resolveEnvString("JIMUQU_AGENT_HEARTBEAT_QUIET_TOKEN", readString(props, overrides, "jimuqu.agent.heartbeat.quietToken", RuntimePathConstants.DEFAULT_HEARTBEAT_QUIET_TOKEN)));
        config.getReact().setMaxSteps(resolveInt("JIMUQU_REACT_MAX_STEPS", readInt(props, overrides, "jimuqu.react.maxSteps", 12)));
        config.getReact().setRetryMax(resolveInt("JIMUQU_REACT_RETRY_MAX", readInt(props, overrides, "jimuqu.react.retryMax", 3)));
        config.getReact().setRetryDelayMs(resolveInt("JIMUQU_REACT_RETRY_DELAY_MS", readInt(props, overrides, "jimuqu.react.retryDelayMs", 2000)));
        config.getReact().setDelegateMaxSteps(resolveInt("JIMUQU_REACT_DELEGATE_MAX_STEPS", readInt(props, overrides, "jimuqu.react.delegateMaxSteps", 18)));
        config.getReact().setDelegateRetryMax(resolveInt("JIMUQU_REACT_DELEGATE_RETRY_MAX", readInt(props, overrides, "jimuqu.react.delegateRetryMax", 4)));
        config.getReact().setDelegateRetryDelayMs(resolveInt("JIMUQU_REACT_DELEGATE_RETRY_DELAY_MS", readInt(props, overrides, "jimuqu.react.delegateRetryDelayMs", 2500)));
        config.getReact().setSummarizationEnabled(resolveBoolean("JIMUQU_REACT_SUMMARIZATION_ENABLED", readBoolean(props, overrides, "jimuqu.react.summarizationEnabled", true)));
        config.getReact().setSummarizationMaxMessages(resolveInt("JIMUQU_REACT_SUMMARIZATION_MAX_MESSAGES", readInt(props, overrides, "jimuqu.react.summarizationMaxMessages", 40)));
        config.getReact().setSummarizationMaxTokens(resolveInt("JIMUQU_REACT_SUMMARIZATION_MAX_TOKENS", readInt(props, overrides, "jimuqu.react.summarizationMaxTokens", 32000)));

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
        if (StrUtil.isBlank(runtime.getConfigOverrideFile())) {
            runtime.setConfigOverrideFile(new File(runtime.getHome(), "config.override.yml").getPath());
        }
        if (StrUtil.isBlank(runtime.getEnvFile())) {
            runtime.setEnvFile(new File(runtime.getHome(), ".env").getPath());
        }
        if (StrUtil.isBlank(runtime.getLogsDir())) {
            runtime.setLogsDir(new File(runtime.getHome(), "logs").getPath());
        }
        runtime.setConfigOverrideFile(asAbsolute(new File(runtime.getConfigOverrideFile()), userDir).getAbsolutePath());
        runtime.setEnvFile(asAbsolute(new File(runtime.getEnvFile()), userDir).getAbsolutePath());
        runtime.setLogsDir(asAbsolute(new File(runtime.getLogsDir()), userDir).getAbsolutePath());
    }

    /**
     * 用新的配置快照覆盖当前实例，保留对象引用稳定。
     */
    public void applyFrom(AppConfig other) {
        if (other == null) {
            return;
        }
        copyRuntime(other.getRuntime());
        copyLlm(other.getLlm());
        copyScheduler(other.getScheduler());
        copyCompression(other.getCompression());
        copyLearning(other.getLearning());
        copyRollback(other.getRollback());
        copyDisplay(other.getDisplay());
        copyReact(other.getReact());
        copyChannel(this.channels.getFeishu(), other.getChannels().getFeishu());
        copyChannel(this.channels.getDingtalk(), other.getChannels().getDingtalk());
        copyChannel(this.channels.getWecom(), other.getChannels().getWecom());
        copyChannel(this.channels.getWeixin(), other.getChannels().getWeixin());
        this.gateway.setAllowedUsers(new ArrayList<String>(other.getGateway().getAllowedUsers()));
        this.gateway.setAllowAllUsers(other.getGateway().isAllowAllUsers());
        this.agent.setPersonalities(clonePersonalities(other.getAgent().getPersonalities()));
    }

    private void copyRuntime(RuntimeConfig other) {
        this.runtime.setHome(other.getHome());
        this.runtime.setContextDir(other.getContextDir());
        this.runtime.setSkillsDir(other.getSkillsDir());
        this.runtime.setCacheDir(other.getCacheDir());
        this.runtime.setStateDb(other.getStateDb());
        this.runtime.setConfigOverrideFile(other.getConfigOverrideFile());
        this.runtime.setEnvFile(other.getEnvFile());
        this.runtime.setLogsDir(other.getLogsDir());
    }

    private void copyLlm(LlmConfig other) {
        this.llm.setProvider(other.getProvider());
        this.llm.setApiUrl(other.getApiUrl());
        this.llm.setApiKey(other.getApiKey());
        this.llm.setModel(other.getModel());
        this.llm.setStream(other.isStream());
        this.llm.setReasoningEffort(other.getReasoningEffort());
        this.llm.setTemperature(other.getTemperature());
        this.llm.setMaxTokens(other.getMaxTokens());
        this.llm.setContextWindowTokens(other.getContextWindowTokens());
    }

    private void copyScheduler(SchedulerConfig other) {
        this.scheduler.setEnabled(other.isEnabled());
        this.scheduler.setTickSeconds(other.getTickSeconds());
    }

    private void copyCompression(CompressionConfig other) {
        this.compression.setEnabled(other.isEnabled());
        this.compression.setThresholdPercent(other.getThresholdPercent());
        this.compression.setSummaryModel(other.getSummaryModel());
        this.compression.setProtectHeadMessages(other.getProtectHeadMessages());
        this.compression.setTailRatio(other.getTailRatio());
    }

    private void copyLearning(LearningConfig other) {
        this.learning.setEnabled(other.isEnabled());
        this.learning.setToolCallThreshold(other.getToolCallThreshold());
    }

    private void copyRollback(RollbackConfig other) {
        this.rollback.setEnabled(other.isEnabled());
        this.rollback.setMaxCheckpointsPerSource(other.getMaxCheckpointsPerSource());
    }

    private void copyDisplay(DisplayConfig other) {
        this.display.setToolProgress(other.getToolProgress());
        this.display.setShowReasoning(other.isShowReasoning());
        this.display.setToolPreviewLength(other.getToolPreviewLength());
        this.display.setProgressThrottleMs(other.getProgressThrottleMs());
    }

    private void copyReact(ReActConfig other) {
        this.react.setMaxSteps(other.getMaxSteps());
        this.react.setRetryMax(other.getRetryMax());
        this.react.setRetryDelayMs(other.getRetryDelayMs());
        this.react.setDelegateMaxSteps(other.getDelegateMaxSteps());
        this.react.setDelegateRetryMax(other.getDelegateRetryMax());
        this.react.setDelegateRetryDelayMs(other.getDelegateRetryDelayMs());
        this.react.setSummarizationEnabled(other.isSummarizationEnabled());
        this.react.setSummarizationMaxMessages(other.getSummarizationMaxMessages());
        this.react.setSummarizationMaxTokens(other.getSummarizationMaxTokens());
    }

    private void copyChannel(ChannelConfig target, ChannelConfig source) {
        target.setEnabled(source.isEnabled());
        target.setAppId(source.getAppId());
        target.setAppSecret(source.getAppSecret());
        target.setClientId(source.getClientId());
        target.setClientSecret(source.getClientSecret());
        target.setBotId(source.getBotId());
        target.setSecret(source.getSecret());
        target.setToken(source.getToken());
        target.setAccountId(source.getAccountId());
        target.setRobotCode(source.getRobotCode());
        target.setCoolAppCode(source.getCoolAppCode());
        target.setWebsocketUrl(source.getWebsocketUrl());
        target.setStreamUrl(source.getStreamUrl());
        target.setLongPollUrl(source.getLongPollUrl());
        target.setBaseUrl(source.getBaseUrl());
        target.setCdnBaseUrl(source.getCdnBaseUrl());
        target.setAllowedUsers(new ArrayList<String>(source.getAllowedUsers()));
        target.setDmPolicy(source.getDmPolicy());
        target.setGroupPolicy(source.getGroupPolicy());
        target.setGroupAllowedUsers(new ArrayList<String>(source.getGroupAllowedUsers()));
        target.setGroupMemberAllowedUsers(cloneGroupAllowMap(source.getGroupMemberAllowedUsers()));
        target.setBotOpenId(source.getBotOpenId());
        target.setBotUserId(source.getBotUserId());
        target.setBotName(source.getBotName());
        target.setAllowAllUsers(source.isAllowAllUsers());
        target.setUnauthorizedDmBehavior(source.getUnauthorizedDmBehavior());
        target.setSplitMultilineMessages(source.isSplitMultilineMessages());
        target.setSendChunkDelaySeconds(source.getSendChunkDelaySeconds());
        target.setSendChunkRetries(source.getSendChunkRetries());
        target.setSendChunkRetryDelaySeconds(source.getSendChunkRetryDelaySeconds());
        target.setToolProgress(source.getToolProgress());
        target.setProgressCardTemplateId(source.getProgressCardTemplateId());
    }

    private Map<String, PersonalityConfig> clonePersonalities(Map<String, PersonalityConfig> source) {
        Map<String, PersonalityConfig> result = new LinkedHashMap<String, PersonalityConfig>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, PersonalityConfig> entry : source.entrySet()) {
            PersonalityConfig config = new PersonalityConfig();
            if (entry.getValue() != null) {
                config.setDescription(entry.getValue().getDescription());
                config.setSystemPrompt(entry.getValue().getSystemPrompt());
                config.setTone(entry.getValue().getTone());
                config.setStyle(entry.getValue().getStyle());
            }
            result.put(entry.getKey(), config);
        }
        return result;
    }

    private Map<String, List<String>> cloneGroupAllowMap(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(entry.getValue()));
        }
        return result;
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
                                           Map<String, Object> overrides,
                                           String channelName,
                                           String allowedUsersEnvName,
                                           String allowAllUsersEnvName,
                                           String unauthorizedBehaviorEnvName,
                                           String dmPolicyEnvName,
                                           String defaultDmPolicy,
                                           String groupPolicyEnvName,
                                           String defaultGroupPolicy,
                                           String groupAllowedUsersEnvName) {
        channelConfig.setAllowedUsers(resolveList(allowedUsersEnvName, readRaw(props, overrides, "jimuqu.channels." + channelName + ".allowedUsers", "")));
        channelConfig.setAllowAllUsers(resolveBoolean(allowAllUsersEnvName, readBoolean(props, overrides, "jimuqu.channels." + channelName + ".allowAllUsers", false)));
        channelConfig.setUnauthorizedDmBehavior(resolveBehavior(
                unauthorizedBehaviorEnvName,
                readString(props, overrides, "jimuqu.channels." + channelName + ".unauthorizedDmBehavior", GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR)
        ));
        channelConfig.setDmPolicy(resolvePolicy(
                dmPolicyEnvName,
                readString(props, overrides, "jimuqu.channels." + channelName + ".dmPolicy", defaultDmPolicy),
                defaultDmPolicy
        ));
        channelConfig.setGroupPolicy(resolvePolicy(
                groupPolicyEnvName,
                readString(props, overrides, "jimuqu.channels." + channelName + ".groupPolicy", defaultGroupPolicy),
                defaultGroupPolicy
        ));
        channelConfig.setGroupAllowedUsers(resolveList(
                groupAllowedUsersEnvName,
                readRaw(props, overrides, "jimuqu.channels." + channelName + ".groupAllowedUsers", "")
        ));
    }

    /**
     * 优先从环境变量解析密钥。
     */
    private static String resolveSecret(String envName, String fallback) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        if (StrUtil.isNotBlank(envValue)) {
            return envValue.trim();
        }
        return StrUtil.nullToEmpty(fallback).trim();
    }

    /**
     * 优先从环境变量解析普通字符串配置。
     */
    private static String resolveEnvString(String envName, String fallback) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        if (StrUtil.isNotBlank(envValue)) {
            return envValue.trim();
        }
        return StrUtil.nullToEmpty(fallback).trim();
    }

    /**
     * 支持通过环境变量覆盖布尔配置。
     */
    private static boolean resolveBoolean(String envName, boolean fallback) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        if (StrUtil.isBlank(envValue)) {
            return fallback;
        }
        String normalized = envValue.trim();
        return "true".equalsIgnoreCase(normalized)
                || "1".equals(normalized)
                || "yes".equalsIgnoreCase(normalized);
    }

    /**
     * 支持通过环境变量覆盖整型配置。
     */
    private static int resolveInt(String envName, int fallback) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        if (StrUtil.isBlank(envValue)) {
            return fallback;
        }
        try {
            return Integer.parseInt(envValue.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * 支持通过环境变量覆盖浮点配置。
     */
    private static double resolveDouble(String envName, double fallback) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        if (StrUtil.isBlank(envValue)) {
            return fallback;
        }
        try {
            return Double.parseDouble(envValue.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * 支持逗号分隔的用户列表解析。
     */
    private static List<String> resolveList(String envName, Object fallback) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        if (StrUtil.isNotBlank(envValue)) {
            return splitList(envValue);
        }
        return splitObjectList(fallback);
    }

    /**
     * 统一收敛未授权私聊用户的处理行为。
     */
    private static String resolveBehavior(String envName, String fallback) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        String value = StrUtil.isNotBlank(envValue)
                ? envValue.trim()
                : StrUtil.nullToDefault(fallback, GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR).trim();
        if (GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE.equalsIgnoreCase(value)) {
            return GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_IGNORE;
        }
        return GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR;
    }

    /**
     * 统一解析访问策略值。
     */
    private static String resolvePolicy(String envName, String fallback, String defaultValue) {
        String envValue = RuntimeEnvResolver.getenv(envName);
        String value = StrUtil.isNotBlank(envValue)
                ? envValue.trim()
                : StrUtil.nullToDefault(fallback, defaultValue).trim();
        return value.length() == 0 ? defaultValue : value.toLowerCase();
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
     * 支持从 YAML 列表或字符串中解析动态 allowlist。
     */
    private static List<String> splitObjectList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof List) {
            List<String> values = new ArrayList<String>();
            for (Object item : (List<?>) raw) {
                if (item != null && String.valueOf(item).trim().length() > 0) {
                    values.add(String.valueOf(item).trim());
                }
            }
            return values;
        }
        return splitList(String.valueOf(raw));
    }

    /**
     * 收集形如 channels.wecom.groups.<groupId>.allowFrom 的动态配置。
     */
    private static Map<String, List<String>> collectGroupAllowMap(Props props,
                                                                  Map<String, Object> overrides,
                                                                  String prefix,
                                                                  String envName) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (props != null) {
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!key.startsWith(prefix) || !key.endsWith(".allowFrom")) {
                    continue;
                }
                String groupId = key.substring(prefix.length(), key.length() - ".allowFrom".length()).trim();
                if (groupId.length() == 0) {
                    continue;
                }
                result.put(groupId, splitObjectList(entry.getValue()));
            }
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix) || !key.endsWith(".allowFrom")) {
                continue;
            }
            String groupId = key.substring(prefix.length(), key.length() - ".allowFrom".length()).trim();
            if (groupId.length() == 0) {
                continue;
            }
            result.put(groupId, splitObjectList(entry.getValue()));
        }
        String envValue = RuntimeEnvResolver.getenv(envName);
        if (StrUtil.isNotBlank(envValue)) {
            result.putAll(parseGroupAllowMapJson(envValue));
        }
        return result;
    }

    /**
     * 支持通过 JSON 环境变量注入 group -> allowlist 映射。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> parseGroupAllowMapJson(String json) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        Object parsed = ONode.deserialize(json, Object.class);
        if (!(parsed instanceof Map)) {
            return result;
        }
        Map<String, Object> raw = (Map<String, Object>) parsed;
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();
            if (key.length() == 0) {
                continue;
            }
            result.put(key, splitObjectList(entry.getValue()));
        }
        return result;
    }

    /**
     * 解析 personalities 配置映射。
     */
    private static Map<String, PersonalityConfig> loadPersonalities(Props props, Map<String, Object> overrides) {
        Map<String, PersonalityConfig> result = new LinkedHashMap<String, PersonalityConfig>();
        if (props == null) {
            return result;
        }

        String prefix = "jimuqu.agent.personalities.";
        Map<String, String> rawEntries = new LinkedHashMap<String, String>();
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String rawKey = String.valueOf(entry.getKey());
            if (!rawKey.startsWith(prefix)) {
                continue;
            }
            rawEntries.put(rawKey, props.get(rawKey, ""));
        }
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                rawEntries.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        for (Map.Entry<String, String> entry : rawEntries.entrySet()) {
            String rawKey = entry.getKey();

            String suffix = rawKey.substring(prefix.length());
            int index = suffix.indexOf('.');
            if (index <= 0 || index >= suffix.length() - 1) {
                continue;
            }

            String name = suffix.substring(0, index).trim();
            String field = suffix.substring(index + 1).trim();
            if (StrUtil.isBlank(name) || StrUtil.isBlank(field)) {
                continue;
            }

            PersonalityConfig personality = result.get(name);
            if (personality == null) {
                personality = new PersonalityConfig();
                result.put(name, personality);
            }

            String value = entry.getValue();
            if ("description".equals(field)) {
                personality.setDescription(value);
            } else if ("systemPrompt".equals(field)) {
                personality.setSystemPrompt(value);
            } else if ("tone".equals(field)) {
                personality.setTone(value);
            } else if ("style".equals(field)) {
                personality.setStyle(value);
            }
        }
        return result;
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

    private static String readString(Props props, Map<String, Object> overrides, String key, String defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            return String.valueOf(override).trim();
        }
        return props.get(key, defaultValue);
    }

    private static Object readRaw(Props props, Map<String, Object> overrides, String key, Object defaultValue) {
        if (overrides.containsKey(key)) {
            return overrides.get(key);
        }
        Object value = props.get(key);
        return value == null ? defaultValue : value;
    }

    private static int readInt(Props props, Map<String, Object> overrides, String key, int defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            try {
                return Integer.parseInt(String.valueOf(override).trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return props.getInt(key, defaultValue);
    }

    private static double readDouble(Props props, Map<String, Object> overrides, String key, double defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            try {
                return Double.parseDouble(String.valueOf(override).trim());
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return props.getDouble(key, defaultValue);
    }

    private static boolean readBoolean(Props props, Map<String, Object> overrides, String key, boolean defaultValue) {
        Object override = overrides.get(key);
        if (override != null) {
            String text = String.valueOf(override).trim();
            return "true".equalsIgnoreCase(text)
                    || "1".equals(text)
                    || "yes".equalsIgnoreCase(text);
        }
        return props.getBool(key, defaultValue);
    }

    private static Map<String, Object> loadFlatOverrides(File runtimeHome) {
        File overrideFile = new File(runtimeHome, "config.override.yml");
        if (!overrideFile.exists()) {
            return Collections.emptyMap();
        }

        try {
            Object parsed = new Yaml().load(FileUtil.readUtf8String(overrideFile));
            if (!(parsed instanceof Map)) {
                return Collections.emptyMap();
            }

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            flatten("", (Map<?, ?>) parsed, result);
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static void flatten(String prefix, Map<?, ?> input, Map<String, Object> output) {
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = prefix.length() == 0 ? String.valueOf(entry.getKey()) : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flatten(key, (Map<?, ?>) value, output);
            } else {
                output.put(key, value);
            }
        }
    }

    private static File asAbsoluteStatic(File file, File base) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(base, file.getPath());
    }

    private static String resolveRuntimePath(String rawValue, String runtimeHome, String legacyDefault, String childName) {
        String value = StrUtil.blankToDefault(rawValue, legacyDefault);
        if (!RuntimePathConstants.RUNTIME_HOME.equals(runtimeHome)
                && legacyDefault.equals(value)) {
            return new File(runtimeHome, childName).getPath();
        }
        return value;
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

        /**
         * runtime/config.override.yml 路径。
         */
        private String configOverrideFile;

        /**
         * runtime/.env 路径。
         */
        private String envFile;

        /**
         * runtime/logs 目录。
         */
        private String logsDir;
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

        /**
         * 模型上下文窗口大小，用于自动压缩阈值计算。
         */
        private int contextWindowTokens;
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
     * 上下文压缩配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CompressionConfig {
        /**
         * 是否启用自动压缩。
         */
        private boolean enabled = true;

        /**
         * 触发压缩的上下文阈值百分比。
         */
        private double thresholdPercent = CompressionConstants.DEFAULT_THRESHOLD_PERCENT;

        /**
         * 可选的压缩摘要模型。
         */
        private String summaryModel;

        /**
         * 头部消息保护数量。
         */
        private int protectHeadMessages = CompressionConstants.DEFAULT_PROTECT_HEAD_MESSAGES;

        /**
         * 尾部消息保护比例。
         */
        private double tailRatio = CompressionConstants.DEFAULT_TAIL_RATIO;
    }

    /**
     * 任务后学习闭环配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LearningConfig {
        /**
         * 是否启用自动学习。
         */
        private boolean enabled = true;

        /**
         * 触发自动学习的最少工具调用数。
         */
        private int toolCallThreshold = 5;
    }

    /**
     * 文件快照与回滚配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RollbackConfig {
        /**
         * 是否启用文件快照与回滚。
         */
        private boolean enabled = true;

        /**
         * 单来源键保留的最大 checkpoint 数。
         */
        private int maxCheckpointsPerSource = CheckpointConstants.DEFAULT_MAX_CHECKPOINTS_PER_SOURCE;
    }

    /**
     * 聊天窗口显示配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DisplayConfig {
        /**
         * 默认工具进度模式。
         */
        private String toolProgress = "off";

        /**
         * 是否默认展示 reasoning。
         */
        private boolean showReasoning;

        /**
         * 工具参数预览长度。
         */
        private int toolPreviewLength = 80;

        /**
         * reasoning/进度消息节流毫秒数。
         */
        private int progressThrottleMs = 1500;
    }

    /**
     * Agent 行为配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class AgentConfig {
        /**
         * 预定义人格列表。
         */
        private Map<String, PersonalityConfig> personalities = new LinkedHashMap<String, PersonalityConfig>();

        /**
         * HEARTBEAT.md 相关调度配置。
         */
        private HeartbeatConfig heartbeat = new HeartbeatConfig();
    }

    /**
     * heartbeat 调度配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class HeartbeatConfig {
        /**
         * 是否启用 heartbeat。
         */
        private boolean enabled = false;

        /**
         * 固定轮询间隔（分钟）。
         */
        private int intervalMinutes = RuntimePathConstants.DEFAULT_HEARTBEAT_INTERVAL_MINUTES;

        /**
         * 结果投递模式。
         */
        private String deliveryMode = RuntimePathConstants.DEFAULT_HEARTBEAT_DELIVERY_MODE;

        /**
         * 静默返回 token。
         */
        private String quietToken = RuntimePathConstants.DEFAULT_HEARTBEAT_QUIET_TOKEN;
    }

    /**
     * ReAct 推理控制配置。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ReActConfig {
        /**
         * 主代理最大推理步数。
         */
        private int maxSteps = 12;

        /**
         * 主代理决策重试次数。
         */
        private int retryMax = 3;

        /**
         * 主代理决策重试基础延迟（毫秒）。
         */
        private int retryDelayMs = 2000;

        /**
         * 子代理最大推理步数。
         */
        private int delegateMaxSteps = 18;

        /**
         * 子代理决策重试次数。
         */
        private int delegateRetryMax = 4;

        /**
         * 子代理决策重试基础延迟（毫秒）。
         */
        private int delegateRetryDelayMs = 2500;

        /**
         * 是否启用 ReAct 工作记忆摘要守卫。
         */
        private boolean summarizationEnabled = true;

        /**
         * ReAct 摘要守卫触发的消息阈值。
         */
        private int summarizationMaxMessages = 40;

        /**
         * ReAct 摘要守卫触发的 token 阈值。
         */
        private int summarizationMaxTokens = 32000;
    }

    /**
     * 单个人格定义。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PersonalityConfig {
        /**
         * 描述文案。
         */
        private String description;

        /**
         * 系统提示词主体。
         */
        private String systemPrompt;

        /**
         * 额外语气提示。
         */
        private String tone;

        /**
         * 额外风格提示。
         */
        private String style;

        /**
         * 合并为最终注入文本。
         */
        public String toPrompt() {
            StringBuilder buffer = new StringBuilder();
            if (StrUtil.isNotBlank(systemPrompt)) {
                buffer.append(systemPrompt.trim());
            }
            if (StrUtil.isNotBlank(tone)) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append("Tone: ").append(tone.trim());
            }
            if (StrUtil.isNotBlank(style)) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append("Style: ").append(style.trim());
            }
            return buffer.toString();
        }
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
         * 微信 accountId。
         */
        private String accountId;

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
         * 渠道基础地址。
         */
        private String baseUrl;

        /**
         * 渠道 CDN 基础地址。
         */
        private String cdnBaseUrl;

        /**
         * 渠道允许名单。
         */
        private List<String> allowedUsers = new ArrayList<String>();

        /**
         * 私聊访问策略。
         */
        private String dmPolicy = GatewayBehaviorConstants.DM_POLICY_OPEN;

        /**
         * 群聊访问策略。
         */
        private String groupPolicy = GatewayBehaviorConstants.GROUP_POLICY_OPEN;

        /**
         * 群聊允许名单。
         */
        private List<String> groupAllowedUsers = new ArrayList<String>();

        /**
         * 企微按群发送者 allowlist。
         */
        private Map<String, List<String>> groupMemberAllowedUsers = new LinkedHashMap<String, List<String>>();

        /**
         * 飞书 bot open id。
         */
        private String botOpenId;

        /**
         * 飞书 bot user id。
         */
        private String botUserId;

        /**
         * 飞书 bot 展示名。
         */
        private String botName;

        /**
         * 是否允许该渠道所有用户访问。
         */
        private boolean allowAllUsers;

        /**
         * 未授权私聊行为。
         */
        private String unauthorizedDmBehavior = GatewayBehaviorConstants.UNAUTHORIZED_DM_BEHAVIOR_PAIR;

        /**
         * 微信是否按多行强制拆分。
         */
        private boolean splitMultilineMessages;

        /**
         * 微信分片发送间隔。
         */
        private double sendChunkDelaySeconds = 0.35D;

        /**
         * 微信分片重试次数。
         */
        private int sendChunkRetries = 2;

        /**
         * 微信分片重试间隔。
         */
        private double sendChunkRetryDelaySeconds = 1.0D;

        /**
         * 渠道默认工具进度模式。
         */
        private String toolProgress;

        /**
         * 钉钉长任务进度卡模板 ID。
         */
        private String progressCardTemplateId;
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
