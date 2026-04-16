package com.jimuqu.agent.support;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.AsyncSkillLearningService;
import com.jimuqu.agent.context.BuiltinMemoryProvider;
import com.jimuqu.agent.context.DefaultMemoryManager;
import com.jimuqu.agent.context.FileMemoryService;
import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.service.ChannelAdapter;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.CommandService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.service.DelegationService;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.core.repository.GatewayPolicyRepository;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.service.MemoryManager;
import com.jimuqu.agent.core.service.MemoryProvider;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.SessionSearchService;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.engine.DefaultContextCompressionService;
import com.jimuqu.agent.engine.DefaultDelegationService;
import com.jimuqu.agent.engine.DefaultConversationOrchestrator;
import com.jimuqu.agent.engine.DefaultSessionSearchService;
import com.jimuqu.agent.gateway.delivery.AdapterBackedDeliveryService;
import com.jimuqu.agent.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.agent.gateway.command.DefaultCommandService;
import com.jimuqu.agent.gateway.service.DefaultGatewayService;
import com.jimuqu.agent.llm.SolonAiLlmGateway;
import com.jimuqu.agent.storage.repository.SqliteCronJobRepository;
import com.jimuqu.agent.storage.repository.SqliteDatabase;
import com.jimuqu.agent.storage.repository.SqliteGlobalSettingRepository;
import com.jimuqu.agent.storage.repository.SqliteGatewayPolicyRepository;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.storage.repository.SqliteSessionRepository;
import com.jimuqu.agent.support.ConversationOrchestratorHolder;
import com.jimuqu.agent.support.DefaultCheckpointService;
import com.jimuqu.agent.tool.runtime.DefaultToolRegistry;
import com.jimuqu.agent.tool.runtime.ProcessRegistry;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TestEnvironment {
    public final AppConfig appConfig;
    public final MemoryChannelAdapter memoryChannelAdapter;
    public final SessionRepository sessionRepository;
    public final CronJobRepository cronJobRepository;
    public final LocalSkillService localSkillService;
    public final DefaultGatewayService gatewayService;
    public final ToolRegistry toolRegistry;
    public final GatewayPolicyRepository gatewayPolicyRepository;
    public final GatewayAuthorizationService gatewayAuthorizationService;
    public final DeliveryService deliveryService;
    public final LlmGateway llmGateway;
    public final MemoryService memoryService;
    public final MemoryManager memoryManager;
    public final CheckpointService checkpointService;
    public final DelegationService delegationService;
    public final ContextCompressionService contextCompressionService;
    public final GlobalSettingRepository globalSettingRepository;
    public final SessionSearchService sessionSearchService;
    public final ProcessRegistry processRegistry;

    public static TestEnvironment withFakeLlm() throws Exception {
        return create(new FakeLlmGateway());
    }

    public static TestEnvironment withLlm(LlmGateway llmGateway) throws Exception {
        return create(llmGateway);
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
        GlobalSettingRepository globalSettingRepository = new SqliteGlobalSettingRepository(database);
        SessionRepository sessionRepository = new SqliteSessionRepository(database);
        CronJobRepository cronJobRepository = new SqliteCronJobRepository(database);
        GatewayPolicyRepository gatewayPolicyRepository = new SqliteGatewayPolicyRepository(database);
        LocalSkillService localSkillService = new LocalSkillService(config, preferenceStore);
        MemoryService memoryService = new FileMemoryService(config);
        MemoryProvider builtinMemoryProvider = new BuiltinMemoryProvider(memoryService);
        MemoryManager memoryManager = new DefaultMemoryManager(java.util.Collections.singletonList(builtinMemoryProvider));
        FileContextService contextService = new FileContextService(config, localSkillService, memoryManager, globalSettingRepository, new File(System.getProperty("user.dir")));
        ContextCompressionService contextCompressionService = new DefaultContextCompressionService(config);
        ConversationOrchestratorHolder holder = new ConversationOrchestratorHolder();
        MemoryChannelAdapter memoryAdapter = new MemoryChannelAdapter();
        Map<PlatformType, ChannelAdapter> adapters = new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(PlatformType.MEMORY, memoryAdapter);
        DeliveryService deliveryService = new AdapterBackedDeliveryService(adapters, gatewayPolicyRepository);
        GatewayAuthorizationService gatewayAuthorizationService = new GatewayAuthorizationService(gatewayPolicyRepository, config);
        CheckpointService checkpointService = new DefaultCheckpointService(config, database);
        ProcessRegistry processRegistry = new ProcessRegistry();
        DelegationService delegationService = new DefaultDelegationService(holder, preferenceStore, sessionRepository);
        SessionSearchService sessionSearchService = new DefaultSessionSearchService(sessionRepository, llmGateway);
        ToolRegistry toolRegistry = new DefaultToolRegistry(config, preferenceStore, sessionRepository, cronJobRepository, deliveryService, processRegistry, memoryService, sessionSearchService, localSkillService, checkpointService, delegationService);
        ConversationOrchestrator orchestrator = new DefaultConversationOrchestrator(sessionRepository, contextService, contextCompressionService, llmGateway, toolRegistry);
        holder.set(orchestrator);
        SkillLearningService skillLearningService = new AsyncSkillLearningService(config, sessionRepository, memoryService, localSkillService, checkpointService);
        CommandService commandService = new DefaultCommandService(sessionRepository, toolRegistry, localSkillService, cronJobRepository, orchestrator, contextService, contextCompressionService, deliveryService, gatewayAuthorizationService, checkpointService, config, globalSettingRepository, processRegistry);
        DefaultGatewayService gatewayService = new DefaultGatewayService(commandService, orchestrator, deliveryService, sessionRepository, gatewayAuthorizationService, skillLearningService, memoryManager);
        return new TestEnvironment(
                config,
                memoryAdapter,
                sessionRepository,
                cronJobRepository,
                localSkillService,
                gatewayService,
                toolRegistry,
                gatewayPolicyRepository,
                gatewayAuthorizationService,
                deliveryService,
                llmGateway,
                memoryService,
                memoryManager,
                checkpointService,
                delegationService,
                contextCompressionService,
                globalSettingRepository,
                sessionSearchService,
                processRegistry
        );
    }

    public GatewayMessage message(String chatId, String userId, String text) {
        return message(chatId, userId, "dm", chatId, userId, text);
    }

    public GatewayMessage message(String chatId, String userId, String chatType, String chatName, String userName, String text) {
        GatewayMessage message = new GatewayMessage(PlatformType.MEMORY, chatId, userId, text);
        message.setChatType(chatType);
        message.setChatName(chatName);
        message.setUserName(userName);
        return message;
    }

    public GatewayReply send(String chatId, String userId, String text) throws Exception {
        return gatewayService.handle(message(chatId, userId, text));
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
        AppConfig.PersonalityConfig helpful = new AppConfig.PersonalityConfig();
        helpful.setDescription("friendly default");
        helpful.setSystemPrompt("You are a helpful assistant.");
        config.getAgent().getPersonalities().put("helpful", helpful);
        AppConfig.PersonalityConfig concise = new AppConfig.PersonalityConfig();
        concise.setDescription("brief answers");
        concise.setSystemPrompt("Be concise.");
        config.getAgent().getPersonalities().put("concise", concise);
        return config;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}

