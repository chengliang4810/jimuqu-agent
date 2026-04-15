package com.jimuqu.agent.bootstrap;

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
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.agent.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.agent.gateway.platform.wecom.WeComChannelAdapter;
import com.jimuqu.agent.gateway.platform.weixin.WeiXinChannelAdapter;
import com.jimuqu.agent.llm.SolonAiLlmGateway;
import com.jimuqu.agent.scheduler.DefaultCronScheduler;
import com.jimuqu.agent.storage.SqliteCronJobRepository;
import com.jimuqu.agent.storage.SqliteDatabase;
import com.jimuqu.agent.storage.SqlitePreferenceStore;
import com.jimuqu.agent.storage.SqliteSessionRepository;
import com.jimuqu.agent.support.ConversationOrchestratorHolder;
import com.jimuqu.agent.tool.DefaultToolRegistry;
import com.jimuqu.agent.tool.ProcessRegistry;
import org.noear.solon.Solon;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class JimuquAgentConfiguration {
    private static final Logger log = LoggerFactory.getLogger(JimuquAgentConfiguration.class);

    @Bean
    public AppConfig appConfig() {
        return AppConfig.load(Solon.cfg());
    }

    @Bean
    public SqliteDatabase sqliteDatabase(AppConfig appConfig) throws Exception {
        return new SqliteDatabase(appConfig);
    }

    @Bean
    public SqlitePreferenceStore sqlitePreferenceStore(SqliteDatabase sqliteDatabase) {
        return new SqlitePreferenceStore(sqliteDatabase);
    }

    @Bean
    public SessionRepository sessionRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteSessionRepository(sqliteDatabase);
    }

    @Bean
    public CronJobRepository cronJobRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteCronJobRepository(sqliteDatabase);
    }

    @Bean
    public LocalSkillService localSkillService(AppConfig appConfig, SqlitePreferenceStore preferenceStore) {
        return new LocalSkillService(appConfig, preferenceStore);
    }

    @Bean
    public FileContextService fileContextService(AppConfig appConfig, LocalSkillService localSkillService) {
        return new FileContextService(appConfig, localSkillService, new File(System.getProperty("user.dir")));
    }

    @Bean
    public LlmGateway llmGateway(AppConfig appConfig) {
        return new SolonAiLlmGateway(appConfig);
    }

    @Bean
    public ProcessRegistry processRegistry() {
        return new ProcessRegistry();
    }

    @Bean
    public ConversationOrchestratorHolder conversationOrchestratorHolder() {
        return new ConversationOrchestratorHolder();
    }

    @Bean
    public Map<PlatformType, ChannelAdapter> channelAdapters(AppConfig appConfig) {
        Map<PlatformType, ChannelAdapter> adapters = new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(PlatformType.FEISHU, new FeishuChannelAdapter(appConfig.getChannels().getFeishu()));
        adapters.put(PlatformType.DINGTALK, new DingTalkChannelAdapter(appConfig.getChannels().getDingtalk()));
        adapters.put(PlatformType.WECOM, new WeComChannelAdapter(appConfig.getChannels().getWecom()));
        adapters.put(PlatformType.WEIXIN, new WeiXinChannelAdapter(appConfig.getChannels().getWeixin()));
        return adapters;
    }

    @Bean
    public DeliveryService deliveryService(Map<PlatformType, ChannelAdapter> channelAdapters) {
        return new AdapterBackedDeliveryService(channelAdapters);
    }

    @Bean
    public ToolRegistry toolRegistry(AppConfig appConfig,
                                     SqlitePreferenceStore preferenceStore,
                                     SessionRepository sessionRepository,
                                     CronJobRepository cronJobRepository,
                                     DeliveryService deliveryService,
                                     ConversationOrchestratorHolder conversationOrchestratorHolder,
                                     ProcessRegistry processRegistry) {
        return new DefaultToolRegistry(appConfig, preferenceStore, sessionRepository, cronJobRepository, deliveryService, conversationOrchestratorHolder, processRegistry);
    }

    @Bean
    public ConversationOrchestrator conversationOrchestrator(SessionRepository sessionRepository,
                                                             FileContextService contextService,
                                                             LlmGateway llmGateway,
                                                             ToolRegistry toolRegistry,
                                                             ConversationOrchestratorHolder holder) {
        ConversationOrchestrator orchestrator = new DefaultConversationOrchestrator(sessionRepository, contextService, llmGateway, toolRegistry);
        holder.set(orchestrator);
        return orchestrator;
    }

    @Bean
    public CommandService commandService(SessionRepository sessionRepository,
                                         ToolRegistry toolRegistry,
                                         LocalSkillService localSkillService,
                                         CronJobRepository cronJobRepository,
                                         ConversationOrchestrator conversationOrchestrator,
                                         DeliveryService deliveryService) {
        return new DefaultCommandService(sessionRepository, toolRegistry, localSkillService, cronJobRepository, conversationOrchestrator, deliveryService);
    }

    @Bean
    public DefaultGatewayService gatewayService(CommandService commandService,
                                                ConversationOrchestrator conversationOrchestrator,
                                                DeliveryService deliveryService,
                                                Map<PlatformType, ChannelAdapter> channelAdapters) {
        DefaultGatewayService service = new DefaultGatewayService(commandService, conversationOrchestrator, deliveryService);
        for (ChannelAdapter adapter : channelAdapters.values()) {
            adapter.setInboundMessageHandler(new com.jimuqu.agent.core.InboundMessageHandler() {
                public void handle(com.jimuqu.agent.core.GatewayMessage message) throws Exception {
                    service.handle(message);
                }
            });
            boolean connected = adapter.connect();
            log.info("[CHANNEL] platform={}, enabled={}, connected={}, detail={}",
                    adapter.platform(),
                    adapter.isEnabled(),
                    connected,
                    adapter.detail());
        }
        return service;
    }

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
