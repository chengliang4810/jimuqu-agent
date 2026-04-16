package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.AsyncSkillLearningService;
import com.jimuqu.agent.context.BuiltinMemoryProvider;
import com.jimuqu.agent.context.DefaultMemoryManager;
import com.jimuqu.agent.context.FileMemoryService;
import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.core.repository.GatewayPolicyRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.ChannelAdapter;
import com.jimuqu.agent.core.service.CommandService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.InboundMessageHandler;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.service.MemoryManager;
import com.jimuqu.agent.core.service.MemoryProvider;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.core.service.DelegationService;
import com.jimuqu.agent.core.service.SessionSearchService;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.engine.DefaultContextCompressionService;
import com.jimuqu.agent.engine.DefaultDelegationService;
import com.jimuqu.agent.engine.DefaultConversationOrchestrator;
import com.jimuqu.agent.engine.DefaultSessionSearchService;
import com.jimuqu.agent.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.agent.gateway.command.DefaultCommandService;
import com.jimuqu.agent.gateway.delivery.AdapterBackedDeliveryService;
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.agent.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.agent.gateway.platform.wecom.WeComChannelAdapter;
import com.jimuqu.agent.gateway.platform.weixin.WeiXinChannelAdapter;
import com.jimuqu.agent.gateway.service.DefaultGatewayService;
import com.jimuqu.agent.llm.SolonAiLlmGateway;
import com.jimuqu.agent.scheduler.DefaultCronScheduler;
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
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Solon Bean 装配配置类，集中定义应用主链所需的核心组件。
 */
@Configuration
public class JimuquAgentConfiguration {
    /**
     * 启动期日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(JimuquAgentConfiguration.class);

    /**
     * 创建应用配置 Bean。
     */
    @Bean
    public AppConfig appConfig() {
        return AppConfig.load(Solon.cfg());
    }

    /**
     * 创建 SQLite 数据库访问对象并初始化表结构。
     */
    @Bean
    public SqliteDatabase sqliteDatabase(AppConfig appConfig) throws Exception {
        return new SqliteDatabase(appConfig);
    }

    /**
     * 创建偏好存储。
     */
    @Bean
    public SqlitePreferenceStore sqlitePreferenceStore(SqliteDatabase sqliteDatabase) {
        return new SqlitePreferenceStore(sqliteDatabase);
    }

    /**
     * 创建会话仓储。
     */
    @Bean
    public SessionRepository sessionRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteSessionRepository(sqliteDatabase);
    }

    /**
     * 创建定时任务仓储。
     */
    @Bean
    public CronJobRepository cronJobRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteCronJobRepository(sqliteDatabase);
    }

    /**
     * 创建本地技能服务。
     */
    @Bean
    public LocalSkillService localSkillService(AppConfig appConfig, SqlitePreferenceStore preferenceStore) {
        return new LocalSkillService(appConfig, preferenceStore);
    }

    /**
     * 创建全局设置仓储。
     */
    @Bean
    public GlobalSettingRepository globalSettingRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteGlobalSettingRepository(sqliteDatabase);
    }

    /**
     * 创建文件上下文服务。
     */
    @Bean
    public FileContextService fileContextService(AppConfig appConfig,
                                                 LocalSkillService localSkillService,
                                                 MemoryManager memoryManager,
                                                 GlobalSettingRepository globalSettingRepository) {
        return new FileContextService(appConfig, localSkillService, memoryManager, globalSettingRepository, new File(System.getProperty("user.dir")));
    }

    /**
     * 创建长期记忆服务。
     */
    @Bean
    public MemoryService memoryService(AppConfig appConfig) {
        return new FileMemoryService(appConfig);
    }

    /**
     * 创建内建记忆提供方。
     */
    @Bean
    public MemoryProvider builtinMemoryProvider(MemoryService memoryService) {
        return new BuiltinMemoryProvider(memoryService);
    }

    /**
     * 创建记忆管理器。
     */
    @Bean
    public MemoryManager memoryManager(MemoryProvider builtinMemoryProvider) {
        java.util.List<MemoryProvider> providers = new java.util.ArrayList<MemoryProvider>();
        providers.add(builtinMemoryProvider);
        return new DefaultMemoryManager(providers);
    }

    /**
     * 创建上下文压缩服务。
     */
    @Bean
    public ContextCompressionService contextCompressionService(AppConfig appConfig) {
        return new DefaultContextCompressionService(appConfig);
    }

    /**
     * 创建大模型网关。
     */
    @Bean
    public LlmGateway llmGateway(AppConfig appConfig) {
        return new SolonAiLlmGateway(appConfig);
    }

    /**
     * 创建会话搜索服务。
     */
    @Bean
    public SessionSearchService sessionSearchService(SessionRepository sessionRepository,
                                                     LlmGateway llmGateway) {
        return new DefaultSessionSearchService(sessionRepository, llmGateway);
    }

    /**
     * 创建进程注册表。
     */
    @Bean
    public ProcessRegistry processRegistry() {
        return new ProcessRegistry();
    }

    /**
     * 创建编排器持有器。
     */
    @Bean
    public ConversationOrchestratorHolder conversationOrchestratorHolder() {
        return new ConversationOrchestratorHolder();
    }

    /**
     * 创建渠道适配器映射。
     */
    @Bean
    public Map<PlatformType, ChannelAdapter> channelAdapters(AppConfig appConfig) {
        Map<PlatformType, ChannelAdapter> adapters = new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(PlatformType.FEISHU, new FeishuChannelAdapter(appConfig.getChannels().getFeishu()));
        adapters.put(PlatformType.DINGTALK, new DingTalkChannelAdapter(appConfig.getChannels().getDingtalk()));
        adapters.put(PlatformType.WECOM, new WeComChannelAdapter(appConfig.getChannels().getWecom()));
        adapters.put(PlatformType.WEIXIN, new WeiXinChannelAdapter(appConfig.getChannels().getWeixin()));
        return adapters;
    }

    /**
     * 创建网关授权策略仓储。
     */
    @Bean
    public GatewayPolicyRepository gatewayPolicyRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteGatewayPolicyRepository(sqliteDatabase);
    }

    /**
     * 创建 checkpoint 服务。
     */
    @Bean
    public CheckpointService checkpointService(AppConfig appConfig, SqliteDatabase sqliteDatabase) {
        return new DefaultCheckpointService(appConfig, sqliteDatabase);
    }

    /**
     * 创建统一投递服务。
     */
    @Bean
    public DeliveryService deliveryService(Map<PlatformType, ChannelAdapter> channelAdapters,
                                           GatewayPolicyRepository gatewayPolicyRepository) {
        return new AdapterBackedDeliveryService(channelAdapters, gatewayPolicyRepository);
    }

    /**
     * 创建授权服务。
     */
    @Bean
    public GatewayAuthorizationService gatewayAuthorizationService(GatewayPolicyRepository gatewayPolicyRepository,
                                                                   AppConfig appConfig) {
        return new GatewayAuthorizationService(gatewayPolicyRepository, appConfig);
    }

    /**
     * 创建工具注册表。
     */
    @Bean
    public ToolRegistry toolRegistry(AppConfig appConfig,
                                     SqlitePreferenceStore preferenceStore,
                                     SessionRepository sessionRepository,
                                     CronJobRepository cronJobRepository,
                                     DeliveryService deliveryService,
                                     ProcessRegistry processRegistry,
                                     MemoryService memoryService,
                                     SessionSearchService sessionSearchService,
                                     LocalSkillService localSkillService,
                                     CheckpointService checkpointService,
                                     DelegationService delegationService) {
        return new DefaultToolRegistry(
                appConfig,
                preferenceStore,
                sessionRepository,
                cronJobRepository,
                deliveryService,
                processRegistry,
                memoryService,
                sessionSearchService,
                localSkillService,
                checkpointService,
                delegationService
        );
    }

    /**
     * 创建对话编排器并同步到持有器。
     */
    @Bean
    public ConversationOrchestrator conversationOrchestrator(SessionRepository sessionRepository,
                                                             FileContextService contextService,
                                                             ContextCompressionService contextCompressionService,
                                                             LlmGateway llmGateway,
                                                             ToolRegistry toolRegistry,
                                                             ConversationOrchestratorHolder holder) {
        ConversationOrchestrator orchestrator = new DefaultConversationOrchestrator(sessionRepository, contextService, contextCompressionService, llmGateway, toolRegistry);
        holder.set(orchestrator);
        return orchestrator;
    }

    /**
     * 创建委托服务。
     */
    @Bean
    public DelegationService delegationService(ConversationOrchestratorHolder holder,
                                               SqlitePreferenceStore preferenceStore,
                                               SessionRepository sessionRepository) {
        return new DefaultDelegationService(holder, preferenceStore, sessionRepository);
    }

    /**
     * 创建任务后学习闭环服务。
     */
    @Bean
    public SkillLearningService skillLearningService(AppConfig appConfig,
                                                     SessionRepository sessionRepository,
                                                     MemoryService memoryService,
                                                     LocalSkillService localSkillService,
                                                     CheckpointService checkpointService) {
        return new AsyncSkillLearningService(appConfig, sessionRepository, memoryService, localSkillService, checkpointService);
    }

    /**
     * 创建命令服务。
     */
    @Bean
    public CommandService commandService(SessionRepository sessionRepository,
                                         ToolRegistry toolRegistry,
                                         LocalSkillService localSkillService,
                                         CronJobRepository cronJobRepository,
                                         ConversationOrchestrator conversationOrchestrator,
                                         FileContextService contextService,
                                         ContextCompressionService contextCompressionService,
                                         DeliveryService deliveryService,
                                         GatewayAuthorizationService gatewayAuthorizationService,
                                         CheckpointService checkpointService,
                                         AppConfig appConfig,
                                         GlobalSettingRepository globalSettingRepository,
                                         ProcessRegistry processRegistry) {
        return new DefaultCommandService(
                sessionRepository,
                toolRegistry,
                localSkillService,
                cronJobRepository,
                conversationOrchestrator,
                contextService,
                contextCompressionService,
                deliveryService,
                gatewayAuthorizationService,
                checkpointService,
                appConfig,
                globalSettingRepository,
                processRegistry
        );
    }

    /**
     * 创建网关服务并为各渠道注入统一入站处理器。
     */
    @Bean
    public DefaultGatewayService gatewayService(CommandService commandService,
                                                ConversationOrchestrator conversationOrchestrator,
                                                DeliveryService deliveryService,
                                                SessionRepository sessionRepository,
                                                GatewayAuthorizationService gatewayAuthorizationService,
                                                SkillLearningService skillLearningService,
                                                MemoryManager memoryManager,
                                                Map<PlatformType, ChannelAdapter> channelAdapters) {
        final DefaultGatewayService service = new DefaultGatewayService(
                commandService,
                conversationOrchestrator,
                deliveryService,
                sessionRepository,
                gatewayAuthorizationService,
                skillLearningService,
                memoryManager
        );

        for (ChannelAdapter adapter : channelAdapters.values()) {
            adapter.setInboundMessageHandler(new InboundMessageHandler() {
                @Override
                public void handle(GatewayMessage message) throws Exception {
                    service.handle(message);
                }
            });

            boolean connected = adapter.connect();
            log.info(
                    "[CHANNEL] platform={}, enabled={}, connected={}, detail={}",
                    adapter.platform(),
                    adapter.isEnabled(),
                    connected,
                    adapter.detail()
            );
        }
        return service;
    }

    /**
     * 创建并启动定时任务调度器。
     */
    @Bean
    public DefaultCronScheduler defaultCronScheduler(AppConfig appConfig,
                                                     CronJobRepository cronJobRepository,
                                                     ConversationOrchestrator conversationOrchestrator,
                                                     DeliveryService deliveryService) {
        DefaultCronScheduler scheduler = new DefaultCronScheduler(appConfig, cronJobRepository, conversationOrchestrator, deliveryService);
        scheduler.start();
        return scheduler;
    }
}
