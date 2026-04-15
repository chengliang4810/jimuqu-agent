package com.jimuqu.agent.config;

import cn.hutool.core.util.StrUtil;
import org.noear.solon.core.Props;

import java.io.File;

public class AppConfig {
    private RuntimeConfig runtime = new RuntimeConfig();
    private LlmConfig llm = new LlmConfig();
    private SchedulerConfig scheduler = new SchedulerConfig();
    private ChannelsConfig channels = new ChannelsConfig();

    public static AppConfig load(Props props) {
        AppConfig config = new AppConfig();

        config.getRuntime().setHome(props.get("jimuqu.runtime.home", "runtime"));
        config.getRuntime().setContextDir(props.get("jimuqu.runtime.contextDir", "runtime/context"));
        config.getRuntime().setSkillsDir(props.get("jimuqu.runtime.skillsDir", "runtime/skills"));
        config.getRuntime().setCacheDir(props.get("jimuqu.runtime.cacheDir", "runtime/cache"));
        config.getRuntime().setStateDb(props.get("jimuqu.runtime.stateDb", "runtime/state.db"));

        config.getLlm().setProvider(props.get("jimuqu.llm.provider", "openai-responses"));
        config.getLlm().setApiUrl(props.get("jimuqu.llm.apiUrl", "https://subapi.jimuqu.com/v1/responses"));
        config.getLlm().setApiKey(resolveSecret("JIMUQU_LLM_API_KEY", props.get("jimuqu.llm.apiKey", "")));
        config.getLlm().setModel(props.get("jimuqu.llm.model", "gpt-5.4"));
        config.getLlm().setStream(props.getBool("jimuqu.llm.stream", false));
        config.getLlm().setReasoningEffort(props.get("jimuqu.llm.reasoningEffort", "medium"));
        config.getLlm().setTemperature(props.getDouble("jimuqu.llm.temperature", 0.2D));
        config.getLlm().setMaxTokens(props.getInt("jimuqu.llm.maxTokens", 4096));

        config.getScheduler().setEnabled(props.getBool("jimuqu.scheduler.enabled", true));
        config.getScheduler().setTickSeconds(props.getInt("jimuqu.scheduler.tickSeconds", 60));

        config.getChannels().getFeishu().setEnabled(props.getBool("jimuqu.channels.feishu.enabled", false));
        config.getChannels().getFeishu().setAppId(resolveSecret("JIMUQU_FEISHU_APP_ID", props.get("jimuqu.channels.feishu.appId", "")));
        config.getChannels().getFeishu().setAppSecret(resolveSecret("JIMUQU_FEISHU_APP_SECRET", props.get("jimuqu.channels.feishu.appSecret", "")));
        config.getChannels().getFeishu().setWebsocketUrl(props.get("jimuqu.channels.feishu.websocketUrl", ""));

        config.getChannels().getDingtalk().setEnabled(resolveBoolean("JIMUQU_DINGTALK_ENABLED", props.getBool("jimuqu.channels.dingtalk.enabled", false)));
        config.getChannels().getDingtalk().setClientId(resolveSecret("JIMUQU_DINGTALK_CLIENT_ID", props.get("jimuqu.channels.dingtalk.clientId", "")));
        config.getChannels().getDingtalk().setClientSecret(resolveSecret("JIMUQU_DINGTALK_CLIENT_SECRET", props.get("jimuqu.channels.dingtalk.clientSecret", "")));
        config.getChannels().getDingtalk().setRobotCode(resolveSecret("JIMUQU_DINGTALK_ROBOT_CODE", props.get("jimuqu.channels.dingtalk.robotCode", "")));
        config.getChannels().getDingtalk().setCoolAppCode(props.get("jimuqu.channels.dingtalk.coolAppCode", ""));
        config.getChannels().getDingtalk().setStreamUrl(props.get("jimuqu.channels.dingtalk.streamUrl", ""));

        config.getChannels().getWecom().setEnabled(props.getBool("jimuqu.channels.wecom.enabled", false));
        config.getChannels().getWecom().setBotId(resolveSecret("JIMUQU_WECOM_BOT_ID", props.get("jimuqu.channels.wecom.botId", "")));
        config.getChannels().getWecom().setSecret(resolveSecret("JIMUQU_WECOM_SECRET", props.get("jimuqu.channels.wecom.secret", "")));
        config.getChannels().getWecom().setWebsocketUrl(props.get("jimuqu.channels.wecom.websocketUrl", ""));

        config.getChannels().getWeixin().setEnabled(props.getBool("jimuqu.channels.weixin.enabled", false));
        config.getChannels().getWeixin().setToken(resolveSecret("JIMUQU_WEIXIN_TOKEN", props.get("jimuqu.channels.weixin.token", "")));
        config.getChannels().getWeixin().setLongPollUrl(props.get("jimuqu.channels.weixin.longPollUrl", ""));

        config.normalizePaths();
        return config;
    }

    private static String resolveSecret(String envName, String fallback) {
        String envValue = System.getenv(envName);
        if (StrUtil.isNotBlank(envValue)) {
            return envValue.trim();
        }

        return StrUtil.nullToEmpty(fallback).trim();
    }

    private static boolean resolveBoolean(String envName, boolean fallback) {
        String envValue = System.getenv(envName);
        if (StrUtil.isBlank(envValue)) {
            return fallback;
        }

        return "true".equalsIgnoreCase(envValue.trim())
                || "1".equals(envValue.trim())
                || "yes".equalsIgnoreCase(envValue.trim());
    }

    public RuntimeConfig getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeConfig runtime) {
        this.runtime = runtime;
    }

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public SchedulerConfig getScheduler() {
        return scheduler;
    }

    public void setScheduler(SchedulerConfig scheduler) {
        this.scheduler = scheduler;
    }

    public ChannelsConfig getChannels() {
        return channels;
    }

    public void setChannels(ChannelsConfig channels) {
        this.channels = channels;
    }

    public void normalizePaths() {
        File base = asAbsolute(new File(getRuntime().getHome()), new File(System.getProperty("user.dir")));
        getRuntime().setHome(base.getAbsolutePath());
        getRuntime().setContextDir(asAbsolute(new File(getRuntime().getContextDir()), base).getAbsolutePath());
        getRuntime().setSkillsDir(asAbsolute(new File(getRuntime().getSkillsDir()), base).getAbsolutePath());
        getRuntime().setCacheDir(asAbsolute(new File(getRuntime().getCacheDir()), base).getAbsolutePath());
        getRuntime().setStateDb(asAbsolute(new File(getRuntime().getStateDb()), base).getAbsolutePath());
    }

    private File asAbsolute(File file, File base) {
        if (file.isAbsolute()) {
            return file;
        }
        return new File(base, file.getPath());
    }

    public static class RuntimeConfig {
        private String home;
        private String contextDir;
        private String skillsDir;
        private String cacheDir;
        private String stateDb;

        public String getHome() {
            return home;
        }

        public void setHome(String home) {
            this.home = home;
        }

        public String getContextDir() {
            return contextDir;
        }

        public void setContextDir(String contextDir) {
            this.contextDir = contextDir;
        }

        public String getSkillsDir() {
            return skillsDir;
        }

        public void setSkillsDir(String skillsDir) {
            this.skillsDir = skillsDir;
        }

        public String getCacheDir() {
            return cacheDir;
        }

        public void setCacheDir(String cacheDir) {
            this.cacheDir = cacheDir;
        }

        public String getStateDb() {
            return stateDb;
        }

        public void setStateDb(String stateDb) {
            this.stateDb = stateDb;
        }
    }

    public static class LlmConfig {
        private String provider;
        private String apiUrl;
        private String apiKey;
        private String model;
        private boolean stream;
        private String reasoningEffort;
        private double temperature;
        private int maxTokens;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isStream() {
            return stream;
        }

        public void setStream(boolean stream) {
            this.stream = stream;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class SchedulerConfig {
        private boolean enabled;
        private int tickSeconds;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTickSeconds() {
            return tickSeconds;
        }

        public void setTickSeconds(int tickSeconds) {
            this.tickSeconds = tickSeconds;
        }
    }

    public static class ChannelsConfig {
        private ChannelConfig feishu = new ChannelConfig();
        private ChannelConfig dingtalk = new ChannelConfig();
        private ChannelConfig wecom = new ChannelConfig();
        private ChannelConfig weixin = new ChannelConfig();

        public ChannelConfig getFeishu() {
            return feishu;
        }

        public void setFeishu(ChannelConfig feishu) {
            this.feishu = feishu;
        }

        public ChannelConfig getDingtalk() {
            return dingtalk;
        }

        public void setDingtalk(ChannelConfig dingtalk) {
            this.dingtalk = dingtalk;
        }

        public ChannelConfig getWecom() {
            return wecom;
        }

        public void setWecom(ChannelConfig wecom) {
            this.wecom = wecom;
        }

        public ChannelConfig getWeixin() {
            return weixin;
        }

        public void setWeixin(ChannelConfig weixin) {
            this.weixin = weixin;
        }
    }

    public static class ChannelConfig {
        private boolean enabled;
        private String appId;
        private String appSecret;
        private String clientId;
        private String clientSecret;
        private String botId;
        private String secret;
        private String token;
        private String robotCode;
        private String coolAppCode;
        private String websocketUrl;
        private String streamUrl;
        private String longPollUrl;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getBotId() {
            return botId;
        }

        public void setBotId(String botId) {
            this.botId = botId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getRobotCode() {
            return robotCode;
        }

        public void setRobotCode(String robotCode) {
            this.robotCode = robotCode;
        }

        public String getCoolAppCode() {
            return coolAppCode;
        }

        public void setCoolAppCode(String coolAppCode) {
            this.coolAppCode = coolAppCode;
        }

        public String getWebsocketUrl() {
            return websocketUrl;
        }

        public void setWebsocketUrl(String websocketUrl) {
            this.websocketUrl = websocketUrl;
        }

        public String getStreamUrl() {
            return streamUrl;
        }

        public void setStreamUrl(String streamUrl) {
            this.streamUrl = streamUrl;
        }

        public String getLongPollUrl() {
            return longPollUrl;
        }

        public void setLongPollUrl(String longPollUrl) {
            this.longPollUrl = longPollUrl;
        }
    }
}
