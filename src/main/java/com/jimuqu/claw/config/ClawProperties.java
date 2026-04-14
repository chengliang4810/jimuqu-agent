package com.jimuqu.claw.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ClawProperties {
    private WorkspaceProperties workspace = new WorkspaceProperties();
    private RuntimeProperties runtime = new RuntimeProperties();
    private Map<String, ProviderProfileProperties> providers = new LinkedHashMap<String, ProviderProfileProperties>();
    private Map<String, ModelAliasProperties> models = new LinkedHashMap<String, ModelAliasProperties>();
    private Map<String, ChannelProperties> channels = new LinkedHashMap<String, ChannelProperties>();
    private JobsProperties jobs = new JobsProperties();
    private SecurityProperties security = new SecurityProperties();
    private TerminalProperties terminal = new TerminalProperties();

    @Data
    public static class WorkspaceProperties {
        private String root = ".";
    }

    @Data
    public static class RuntimeProperties {
        private String defaultModel = "default";
        private Integer maxSteps = 24;
        private Integer sessionWindowSize = 40;
        private Integer delegateDepthLimit = 2;
        private Long childTimeoutMs = 300000L;
        private String systemPromptResource = "classpath:prompts/system-base.md";
    }

    @Data
    public static class ProviderProfileProperties {
        private String dialect;
        private String baseUrl;
        private String apiKey;
        private String token;
        private Long timeoutMs = 60000L;
        private Map<String, String> headers = new LinkedHashMap<String, String>();
    }

    @Data
    public static class ModelAliasProperties {
        private String providerProfile;
        private String model;
        private Double temperature;
        private Long maxTokens;
        private Long maxOutputTokens;
        private Map<String, String> headers = new LinkedHashMap<String, String>();
    }

    @Data
    public static class ChannelProperties {
        private Boolean enabled = Boolean.FALSE;
        private String verifyToken;
        private String webhookUrl;
        private String appId;
        private String appSecret;
        private String appKey;
        private String appToken;
        private String botId;
        private String agentId;
        private String corpId;
        private String corpSecret;
        private String aesKey;
        private String homeChatId;
    }

    @Data
    public static class JobsProperties {
        private Long pollIntervalMs = 1000L;
    }

    @Data
    public static class SecurityProperties {
        private Boolean auditEnabled = Boolean.FALSE;
        private Boolean allowExplicitSendTargets = Boolean.TRUE;
        private Boolean requireTerminalApproval = Boolean.FALSE;
    }

    @Data
    public static class TerminalProperties {
        private Long defaultTimeoutMs = 120000L;
        private Integer maxConcurrentProcesses = 4;
        private List<String> allowedCommands = new ArrayList<String>();
    }
}
