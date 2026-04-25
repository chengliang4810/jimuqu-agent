package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.agent.AgentProfileService;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.FileContextService;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
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
import com.jimuqu.agent.core.service.MemoryManager;
import com.jimuqu.agent.core.service.SkillHubService;
import com.jimuqu.agent.core.service.SkillLearningService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.gateway.authorization.GatewayAuthorizationService;
import com.jimuqu.agent.gateway.command.DefaultCommandService;
import com.jimuqu.agent.gateway.delivery.AdapterBackedDeliveryService;
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.agent.gateway.platform.feishu.FeishuChannelAdapter;
import com.jimuqu.agent.gateway.platform.wecom.WeComChannelAdapter;
import com.jimuqu.agent.gateway.platform.weixin.WeiXinChannelAdapter;
import com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.agent.gateway.service.ChannelConnectionManager;
import com.jimuqu.agent.gateway.service.DefaultGatewayService;
import com.jimuqu.agent.gateway.service.GatewayInjectionAuthService;
import com.jimuqu.agent.project.service.ProjectService;
import com.jimuqu.agent.support.AttachmentCacheService;
import com.jimuqu.agent.support.DisplaySettingsService;
import com.jimuqu.agent.support.LlmProviderService;
import com.jimuqu.agent.support.RuntimeSettingsService;
import com.jimuqu.agent.support.update.AppUpdateService;
import com.jimuqu.agent.support.update.AppVersionService;
import com.jimuqu.agent.tool.runtime.DangerousCommandApprovalService;
import com.jimuqu.agent.tool.runtime.ProcessRegistry;
import com.jimuqu.agent.web.DashboardConfigService;
import com.jimuqu.agent.web.DashboardRuntimeConfigService;
import com.jimuqu.agent.web.DashboardProviderService;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * gateway bean configuration.
 */
@Configuration
public class GatewayConfiguration {
    @Bean
    public Map<PlatformType, ChannelAdapter> channelAdapters(AppConfig appConfig,
                                                             ChannelStateRepository channelStateRepository,
                                                             AttachmentCacheService attachmentCacheService) {
        Map<PlatformType, ChannelAdapter> adapters = new LinkedHashMap<PlatformType, ChannelAdapter>();
        adapters.put(PlatformType.FEISHU, new FeishuChannelAdapter(appConfig.getChannels().getFeishu(), attachmentCacheService));
        adapters.put(PlatformType.DINGTALK, new DingTalkChannelAdapter(appConfig.getChannels().getDingtalk(), channelStateRepository, attachmentCacheService));
        adapters.put(PlatformType.WECOM, new WeComChannelAdapter(appConfig.getChannels().getWecom(), attachmentCacheService));
        adapters.put(PlatformType.WEIXIN, new WeiXinChannelAdapter(appConfig.getChannels().getWeixin(), channelStateRepository, attachmentCacheService));
        return adapters;
    }

    @Bean
    public DeliveryService deliveryService(Map<PlatformType, ChannelAdapter> channelAdapters,
                                           GatewayPolicyRepository gatewayPolicyRepository) {
        return new AdapterBackedDeliveryService(channelAdapters, gatewayPolicyRepository);
    }

    @Bean
    public RuntimeSettingsService runtimeSettingsService(AppConfig appConfig,
                                                         GlobalSettingRepository globalSettingRepository,
                                                         DeliveryService deliveryService,
                                                         DashboardConfigService dashboardConfigService,
                                                         DashboardRuntimeConfigService dashboardRuntimeConfigService,
                                                         AppVersionService appVersionService,
                                                         LlmProviderService llmProviderService,
                                                         DashboardProviderService dashboardProviderService) {
        return new RuntimeSettingsService(appConfig, globalSettingRepository, deliveryService, dashboardConfigService, dashboardRuntimeConfigService, appVersionService, llmProviderService, dashboardProviderService);
    }

    @Bean(destroyMethod = "shutdown")
    public ChannelConnectionManager channelConnectionManager(Map<PlatformType, ChannelAdapter> channelAdapters) {
        return new ChannelConnectionManager(channelAdapters);
    }

    @Bean
    public GatewayRuntimeRefreshService gatewayRuntimeRefreshService(AppConfig appConfig,
                                                                     ChannelConnectionManager channelConnectionManager) {
        return new GatewayRuntimeRefreshService(appConfig, channelConnectionManager);
    }

    @Bean
    public GatewayAuthorizationService gatewayAuthorizationService(GatewayPolicyRepository gatewayPolicyRepository,
                                                                   AppConfig appConfig) {
        return new GatewayAuthorizationService(gatewayPolicyRepository, appConfig);
    }

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
                                         SkillHubService skillHubService,
                                         AppConfig appConfig,
                                         GlobalSettingRepository globalSettingRepository,
                                         ProcessRegistry processRegistry,
                                         RuntimeSettingsService runtimeSettingsService,
                                         DisplaySettingsService displaySettingsService,
                                         AppUpdateService appUpdateService,
                                         DangerousCommandApprovalService dangerousCommandApprovalService,
                                         AgentProfileService agentProfileService,
                                         ProjectService projectService) {
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
                skillHubService,
                appConfig,
                globalSettingRepository,
                processRegistry,
                runtimeSettingsService,
                displaySettingsService,
                appUpdateService,
                dangerousCommandApprovalService,
                agentProfileService,
                projectService
        );
    }

    @Bean
    public DefaultGatewayService gatewayService(CommandService commandService,
                                                ConversationOrchestrator conversationOrchestrator,
                                                DeliveryService deliveryService,
                                                SessionRepository sessionRepository,
                                                GatewayAuthorizationService gatewayAuthorizationService,
                                                SkillLearningService skillLearningService,
                                                MemoryManager memoryManager,
                                                ChannelConnectionManager channelConnectionManager) {
        final DefaultGatewayService service = new DefaultGatewayService(
                commandService,
                conversationOrchestrator,
                deliveryService,
                sessionRepository,
                gatewayAuthorizationService,
                skillLearningService,
                memoryManager
        );

        channelConnectionManager.bindInboundHandler(new InboundMessageHandler() {
            @Override
            public void handle(GatewayMessage message) throws Exception {
                service.handle(message);
            }
        });
        channelConnectionManager.startAll();
        return service;
    }

    @Bean
    public GatewayInjectionAuthService gatewayInjectionAuthService(AppConfig appConfig) {
        return new GatewayInjectionAuthService(appConfig);
    }
}
