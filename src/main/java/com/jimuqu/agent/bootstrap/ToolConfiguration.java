package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.AgentRunRepository;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.ContextCompressionService;
import com.jimuqu.agent.core.service.ContextBudgetService;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.LlmGateway;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.core.service.DelegationService;
import com.jimuqu.agent.core.service.SessionSearchService;
import com.jimuqu.agent.core.service.SkillHubService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.engine.DefaultDelegationService;
import com.jimuqu.agent.engine.AgentRunSupervisor;
import com.jimuqu.agent.engine.DefaultConversationOrchestrator;
import com.jimuqu.agent.llm.SolonAiLlmGateway;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.ConversationOrchestratorHolder;
import com.jimuqu.agent.support.AttachmentCacheService;
import com.jimuqu.agent.support.DisplaySettingsService;
import com.jimuqu.agent.support.LlmProviderService;
import com.jimuqu.agent.support.RuntimePathGuard;
import com.jimuqu.agent.support.RuntimeSettingsService;
import com.jimuqu.agent.support.update.AppUpdateService;
import com.jimuqu.agent.support.update.AppVersionService;
import com.jimuqu.agent.tool.runtime.DangerousCommandApprovalService;
import com.jimuqu.agent.tool.runtime.DefaultToolRegistry;
import com.jimuqu.agent.tool.runtime.ProcessRegistry;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/**
 * tool bean configuration.
 */
@Configuration
public class ToolConfiguration {
    @Bean
    public ProcessRegistry processRegistry() {
        return new ProcessRegistry();
    }

    @Bean
    public DangerousCommandApprovalService dangerousCommandApprovalService(GlobalSettingRepository globalSettingRepository) {
        return new DangerousCommandApprovalService(globalSettingRepository);
    }

    @Bean
    public AttachmentCacheService attachmentCacheService(AppConfig appConfig) {
        return new AttachmentCacheService(appConfig);
    }

    @Bean
    public RuntimePathGuard runtimePathGuard(AppConfig appConfig) {
        return new RuntimePathGuard(appConfig);
    }

    @Bean
    public ToolRegistry toolRegistry(AppConfig appConfig,
                                     SqlitePreferenceStore preferenceStore,
                                     SessionRepository sessionRepository,
                                     CronJobRepository cronJobRepository,
                                     DeliveryService deliveryService,
                                     MemoryService memoryService,
                                     SessionSearchService sessionSearchService,
                                     LocalSkillService localSkillService,
                                     SkillHubService skillHubService,
                                     CheckpointService checkpointService,
                                     DelegationService delegationService,
                                     AttachmentCacheService attachmentCacheService,
                                     RuntimeSettingsService runtimeSettingsService,
                                     RuntimePathGuard runtimePathGuard) {
        return new DefaultToolRegistry(
                appConfig,
                preferenceStore,
                sessionRepository,
                cronJobRepository,
                deliveryService,
                memoryService,
                sessionSearchService,
                localSkillService,
                skillHubService,
                checkpointService,
                delegationService,
                attachmentCacheService,
                runtimeSettingsService,
                runtimePathGuard
        );
    }

    @Bean
    public DelegationService delegationService(ConversationOrchestratorHolder holder,
                                               SqlitePreferenceStore preferenceStore,
                                               SessionRepository sessionRepository) {
        return new DefaultDelegationService(holder, preferenceStore, sessionRepository);
    }

    @Bean
    public DisplaySettingsService displaySettingsService(AppConfig appConfig,
                                                         GlobalSettingRepository globalSettingRepository) {
        return new DisplaySettingsService(appConfig, globalSettingRepository);
    }

    @Bean
    public AppVersionService appVersionService(AppConfig appConfig) {
        return new AppVersionService(appConfig);
    }

    @Bean(destroyMethod = "shutdown")
    public AppUpdateService appUpdateService(AppConfig appConfig,
                                             AppVersionService appVersionService) {
        return new AppUpdateService(appConfig, appVersionService);
    }

    @Bean
    public LlmProviderService llmProviderService(AppConfig appConfig) {
        return new LlmProviderService(appConfig);
    }

    @Bean
    public ConversationOrchestratorHolder conversationOrchestratorHolder() {
        return new ConversationOrchestratorHolder();
    }

    @Bean
    public LlmGateway llmGateway(AppConfig appConfig,
                                 SessionRepository sessionRepository,
                                 DangerousCommandApprovalService dangerousCommandApprovalService,
                                 LlmProviderService llmProviderService) {
        return new SolonAiLlmGateway(appConfig, sessionRepository, dangerousCommandApprovalService, llmProviderService);
    }

    @Bean
    public AgentRunSupervisor agentRunSupervisor(AppConfig appConfig,
                                                 SessionRepository sessionRepository,
                                                 AgentRunRepository agentRunRepository,
                                                 ContextCompressionService contextCompressionService,
                                                 ContextBudgetService contextBudgetService,
                                                 LlmGateway llmGateway,
                                                 LlmProviderService llmProviderService) {
        return new AgentRunSupervisor(
                appConfig,
                sessionRepository,
                agentRunRepository,
                contextCompressionService,
                contextBudgetService,
                llmGateway,
                llmProviderService
        );
    }

    @Bean
    public ConversationOrchestrator conversationOrchestrator(SessionRepository sessionRepository,
                                                             FileContextService contextService,
                                                             ContextCompressionService contextCompressionService,
                                                             LlmGateway llmGateway,
                                                             ToolRegistry toolRegistry,
                                                             DeliveryService deliveryService,
                                                             DisplaySettingsService displaySettingsService,
                                                             ConversationOrchestratorHolder holder,
                                                             RuntimeSettingsService runtimeSettingsService,
                                                             DangerousCommandApprovalService dangerousCommandApprovalService,
                                                             AgentRunSupervisor agentRunSupervisor) {
        ConversationOrchestrator orchestrator = new DefaultConversationOrchestrator(
                sessionRepository,
                contextService,
                contextCompressionService,
                llmGateway,
                toolRegistry,
                deliveryService,
                displaySettingsService,
                runtimeSettingsService,
                dangerousCommandApprovalService,
                agentRunSupervisor
        );
        holder.set(orchestrator);
        return orchestrator;
    }
}
