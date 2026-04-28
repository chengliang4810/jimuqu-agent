package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.context.PersonaWorkspaceService;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.AgentRunRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.gateway.service.GatewayRuntimeRefreshService;
import com.jimuqu.agent.scheduler.DefaultCronScheduler;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.LlmProviderService;
import com.jimuqu.agent.support.update.AppUpdateService;
import com.jimuqu.agent.support.update.AppVersionService;
import com.jimuqu.agent.web.DashboardAuthFilter;
import com.jimuqu.agent.web.DashboardAuthService;
import com.jimuqu.agent.web.DashboardAnalyticsService;
import com.jimuqu.agent.web.DashboardConfigService;
import com.jimuqu.agent.web.DashboardCronService;
import com.jimuqu.agent.web.DashboardRuntimeConfigService;
import com.jimuqu.agent.web.DashboardRunService;
import com.jimuqu.agent.web.DashboardDiagnosticsService;
import com.jimuqu.agent.web.DashboardGatewayDoctorService;
import com.jimuqu.agent.web.DashboardLogsService;
import com.jimuqu.agent.web.DashboardProviderService;
import com.jimuqu.agent.web.DashboardSessionService;
import com.jimuqu.agent.web.DashboardSkillsService;
import com.jimuqu.agent.web.DashboardStatusService;
import com.jimuqu.agent.web.DashboardWorkspaceService;
import com.jimuqu.agent.web.WeixinQrSetupService;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.core.handle.Filter;

/**
 * dashboard bean configuration.
 */
@Configuration
public class DashboardConfiguration {
    @Bean
    public DashboardAuthService dashboardAuthService(AppConfig appConfig) {
        return new DashboardAuthService(appConfig);
    }

    @Bean
    public Filter dashboardAuthFilter(DashboardAuthService dashboardAuthService) {
        return new DashboardAuthFilter(dashboardAuthService);
    }

    @Bean
    public DashboardStatusService dashboardStatusService(AppConfig appConfig,
                                                         SessionRepository sessionRepository,
                                                         DeliveryService deliveryService,
                                                         GatewayRuntimeRefreshService gatewayRuntimeRefreshService,
                                                         AppVersionService appVersionService,
                                                         AppUpdateService appUpdateService,
                                                         LlmProviderService llmProviderService) {
        return new DashboardStatusService(appConfig, sessionRepository, deliveryService, gatewayRuntimeRefreshService, appVersionService, appUpdateService, llmProviderService);
    }

    @Bean
    public DashboardSessionService dashboardSessionService(SessionRepository sessionRepository,
                                                           CheckpointService checkpointService) {
        return new DashboardSessionService(sessionRepository, checkpointService);
    }

    @Bean
    public DashboardRunService dashboardRunService(AgentRunRepository agentRunRepository) {
        return new DashboardRunService(agentRunRepository);
    }

    @Bean
    public DashboardDiagnosticsService dashboardDiagnosticsService(AppConfig appConfig,
                                                                   DeliveryService deliveryService,
                                                                   LlmProviderService llmProviderService,
                                                                   ToolRegistry toolRegistry) {
        return new DashboardDiagnosticsService(appConfig, deliveryService, llmProviderService, toolRegistry);
    }

    @Bean
    public DashboardAnalyticsService dashboardAnalyticsService(SessionRepository sessionRepository) {
        return new DashboardAnalyticsService(sessionRepository);
    }

    @Bean
    public DashboardLogsService dashboardLogsService(AppConfig appConfig) {
        return new DashboardLogsService(appConfig);
    }

    @Bean
    public DashboardConfigService dashboardConfigService(AppConfig appConfig,
                                                         GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        return new DashboardConfigService(appConfig, gatewayRuntimeRefreshService);
    }

    @Bean
    public DashboardProviderService dashboardProviderService(AppConfig appConfig,
                                                             GatewayRuntimeRefreshService gatewayRuntimeRefreshService,
                                                             LlmProviderService llmProviderService) {
        return new DashboardProviderService(appConfig, gatewayRuntimeRefreshService, llmProviderService);
    }

    @Bean
    public DashboardWorkspaceService dashboardWorkspaceService(PersonaWorkspaceService personaWorkspaceService) {
        return new DashboardWorkspaceService(personaWorkspaceService);
    }

    @Bean
    public DashboardRuntimeConfigService dashboardRuntimeConfigService(AppConfig appConfig,
                                                   GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        return new DashboardRuntimeConfigService(appConfig, gatewayRuntimeRefreshService);
    }

    @Bean
    public DashboardGatewayDoctorService dashboardGatewayDoctorService(AppConfig appConfig,
                                                                       DeliveryService deliveryService,
                                                                       GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        return new DashboardGatewayDoctorService(appConfig, deliveryService, gatewayRuntimeRefreshService);
    }

    @Bean(destroyMethod = "shutdown")
    public WeixinQrSetupService weixinQrSetupService(AppConfig appConfig,
                                                     DashboardConfigService dashboardConfigService,
                                                     GatewayRuntimeRefreshService gatewayRuntimeRefreshService) {
        return new WeixinQrSetupService(appConfig, dashboardConfigService, gatewayRuntimeRefreshService);
    }

    @Bean
    public DashboardSkillsService dashboardSkillsService(LocalSkillService localSkillService,
                                                         SqlitePreferenceStore sqlitePreferenceStore) {
        return new DashboardSkillsService(localSkillService, sqlitePreferenceStore);
    }

    @Bean
    public DashboardCronService dashboardCronService(CronJobRepository cronJobRepository,
                                                     DefaultCronScheduler defaultCronScheduler) {
        return new DashboardCronService(cronJobRepository, defaultCronScheduler);
    }
}
