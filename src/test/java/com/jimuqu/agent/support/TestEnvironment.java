package com.jimuqu.agent.support;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.ChannelAdapter;
import com.jimuqu.agent.core.CommandService;
import com.jimuqu.agent.core.ConversationOrchestrator;
import com.jimuqu.agent.core.CronJobRepository;
import com.jimuqu.agent.core.DeliveryService;
import com.jimuqu.agent.core.LlmGateway;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.core.SessionRepository;
import com.jimuqu.agent.core.ToolRegistry;
import com.jimuqu.agent.engine.DefaultConversationOrchestrator;
import com.jimuqu.agent.gateway.AdapterBackedDeliveryService;
import com.jimuqu.agent.gateway.DefaultCommandService;
import com.jimuqu.agent.gateway.DefaultGatewayService;
import com.jimuqu.agent.llm.SolonAiLlmGateway;
import com.jimuqu.agent.storage.SqliteCronJobRepository;
import com.jimuqu.agent.storage.SqliteDatabase;
import com.jimuqu.agent.storage.SqlitePreferenceStore;
import com.jimuqu.agent.storage.SqliteSessionRepository;
import com.jimuqu.agent.support.ConversationOrchestratorHolder;
import com.jimuqu.agent.tool.DefaultToolRegistry;
import com.jimuqu.agent.tool.ProcessRegistry;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestEnvironment {
    public final AppConfig appConfig;
    public final MemoryChannelAdapter memoryChannelAdapter;
    public final SessionRepository sessionRepository;
    public final CronJobRepository cronJobRepository;
    public final LocalSkillService localSkillService;
    public final DefaultGatewayService gatewayService;

    private TestEnvironment(AppConfig appConfig,
                            MemoryChannelAdapter memoryChannelAdapter,
                            SessionRepository sessionRepository,
                            CronJobRepository cronJobRepository,
                            LocalSkillService localSkillService,
                            DefaultGatewayService gatewayService) {
        this.appConfig = appConfig;
        this.memoryChannelAdapter = memoryChannelAdapter;
        this.sessionRepository = sessionRepository;
        this.cronJobRepository = cronJobRepository;
        this.localSkillService = localSkillService;
        this.gatewayService = gatewayService;
    }

    public static TestEnvironment withFakeLlm() throws Exception {
        return create(new FakeLlmGateway());
    }

    public static TestEnvironment withLiveLlm() throws Exception {
        AppConfig config = newConfig();
        config.getLlm().setProvider("openai-responses");
        config.getLlm().setApiUrl(envOrDefault("JIMUQU_LIVE_AI_BASE_URL", "https://subapi.jimuqu.com/v1/responses"));
        config.getLlm().setApiKey(envOrDefault("JIMUQU_LIVE_AI_KEY", ""));
        config.getLlm().setModel(envOrDefault("JIMUQU_LIVE_AI_MODEL", "gpt-5.4"));
        config.getLlm().setStream(true);
        return create(config, new SolonAiLlmGateway(config));
    }

    private static TestEnvironment create(LlmGateway llmGateway) throws Exception {
        return create(newConfig(), llmGateway);
    }

    private static TestEnvironment create(AppConfig config, LlmGateway llmGateway) throws Exception {
        SqliteDatabase database = new SqliteDatabase(config);
        SqlitePreferenceStore preferenceStore = new SqlitePreferenceStore(database);
        SessionRepository sessionRepository = new SqliteSessionRepository(database);
        CronJobRepository cronJobRepository = new SqliteCronJobRepository(database);
        LocalSkillService localSkillService = new LocalSkillService(config, preferenceStore);
        FileContextService contextService = new FileContextService(config, localSkillService, new File(System.getProperty("user.dir")));
        ConversationOrchestratorHolder holder = new ConversationOrchestratorHolder();
        MemoryChannelAdapter memoryAdapter = new MemoryChannelAdapter();
        Map<PlatformType, ChannelAdapter> adapters = new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(PlatformType.MEMORY, memoryAdapter);
        DeliveryService deliveryService = new AdapterBackedDeliveryService(adapters);
        ProcessRegistry processRegistry = new ProcessRegistry();
        ToolRegistry toolRegistry = new DefaultToolRegistry(config, preferenceStore, sessionRepository, cronJobRepository, deliveryService, holder, processRegistry);
        ConversationOrchestrator orchestrator = new DefaultConversationOrchestrator(sessionRepository, contextService, llmGateway, toolRegistry);
        holder.set(orchestrator);
        CommandService commandService = new DefaultCommandService(sessionRepository, toolRegistry, localSkillService, cronJobRepository, orchestrator, deliveryService);
        DefaultGatewayService gatewayService = new DefaultGatewayService(commandService, orchestrator, deliveryService);
        return new TestEnvironment(config, memoryAdapter, sessionRepository, cronJobRepository, localSkillService, gatewayService);
    }

    private static AppConfig newConfig() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-agent-test").toFile();
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.getRuntime().setContextDir(new File(runtimeHome, "context").getAbsolutePath());
        config.getRuntime().setSkillsDir(new File(runtimeHome, "skills").getAbsolutePath());
        config.getRuntime().setCacheDir(new File(runtimeHome, "cache").getAbsolutePath());
        config.getRuntime().setStateDb(new File(runtimeHome, "state.db").getAbsolutePath());
        config.getLlm().setProvider("openai-responses");
        config.getLlm().setApiUrl("https://subapi.jimuqu.com/v1/responses");
        config.getLlm().setModel("gpt-5.4");
        config.getLlm().setReasoningEffort("medium");
        config.getLlm().setTemperature(0.2D);
        config.getLlm().setMaxTokens(4096);
        config.getScheduler().setEnabled(false);
        return config;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
