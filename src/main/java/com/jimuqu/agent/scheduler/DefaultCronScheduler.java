package com.jimuqu.agent.scheduler;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.ConversationOrchestrator;
import com.jimuqu.agent.core.CronJobRecord;
import com.jimuqu.agent.core.CronJobRepository;
import com.jimuqu.agent.core.DeliveryService;
import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.support.CronSupport;
import com.jimuqu.agent.support.SourceKeySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultCronScheduler {
    private static final Logger log = LoggerFactory.getLogger(DefaultCronScheduler.class);

    private final AppConfig appConfig;
    private final CronJobRepository cronJobRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final DeliveryService deliveryService;
    private ScheduledExecutorService executorService;

    public DefaultCronScheduler(AppConfig appConfig,
                                CronJobRepository cronJobRepository,
                                ConversationOrchestrator conversationOrchestrator,
                                DeliveryService deliveryService) {
        this.appConfig = appConfig;
        this.cronJobRepository = cronJobRepository;
        this.conversationOrchestrator = conversationOrchestrator;
        this.deliveryService = deliveryService;
    }

    public void start() {
        if (!appConfig.getScheduler().isEnabled()) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(this::tickSafe, 5, appConfig.getScheduler().getTickSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    public void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            log.warn("Cron tick failed", e);
        }
    }

    public void tick() throws Exception {
        long now = System.currentTimeMillis();
        List<CronJobRecord> jobs = cronJobRepository.listDue(now);
        for (CronJobRecord job : jobs) {
            execute(job, now);
        }
    }

    public void runNow(String jobId) throws Exception {
        CronJobRecord job = cronJobRepository.findById(jobId);
        if (job != null) {
            execute(job, System.currentTimeMillis());
        }
    }

    private void execute(CronJobRecord job, long now) throws Exception {
        String[] parts = SourceKeySupport.split(job.getSourceKey());
        GatewayMessage synthetic = new GatewayMessage(PlatformType.fromName(parts[0]), parts[1], parts[2], job.getPrompt());
        GatewayReply reply = conversationOrchestrator.runScheduled(synthetic);
        deliveryService.deliver(SourceKeySupport.toDeliveryRequest(job.getSourceKey(), reply.getContent()));
        cronJobRepository.markRun(job.getJobId(), now, CronSupport.nextRunAt(job.getCronExpr(), now));
    }
}
