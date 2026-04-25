package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.PersonaWorkspaceService;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.GatewayPolicyRepository;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.scheduler.DefaultCronScheduler;
import com.jimuqu.agent.scheduler.HeartbeatScheduler;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/**
 * scheduler bean configuration.
 */
@Configuration
public class SchedulerConfiguration {
    @Bean(destroyMethod = "shutdown")
    public DefaultCronScheduler defaultCronScheduler(AppConfig appConfig,
                                                     CronJobRepository cronJobRepository,
                                                     ConversationOrchestrator conversationOrchestrator,
                                                     DeliveryService deliveryService) {
        DefaultCronScheduler scheduler = new DefaultCronScheduler(appConfig, cronJobRepository, conversationOrchestrator, deliveryService);
        scheduler.start();
        return scheduler;
    }

    @Bean(destroyMethod = "shutdown")
    public HeartbeatScheduler heartbeatScheduler(AppConfig appConfig,
                                                 GatewayPolicyRepository gatewayPolicyRepository,
                                                 ConversationOrchestrator conversationOrchestrator,
                                                 DeliveryService deliveryService,
                                                 PersonaWorkspaceService personaWorkspaceService) {
        HeartbeatScheduler scheduler = new HeartbeatScheduler(
                appConfig,
                gatewayPolicyRepository,
                conversationOrchestrator,
                deliveryService,
                personaWorkspaceService
        );
        scheduler.start();
        return scheduler;
    }
}
