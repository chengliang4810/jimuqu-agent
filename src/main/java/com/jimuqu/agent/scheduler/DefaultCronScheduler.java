package com.jimuqu.agent.scheduler;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.model.CronJobRecord;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.support.CronSupport;
import com.jimuqu.agent.support.SourceKeySupport;
import org.noear.solon.Utils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DefaultCronScheduler 实现。
 */
@RequiredArgsConstructor
public class DefaultCronScheduler {
    private static final Logger log = LoggerFactory.getLogger(DefaultCronScheduler.class);

    private final AppConfig appConfig;
    private final CronJobRepository cronJobRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final DeliveryService deliveryService;
    private ScheduledExecutorService executorService;

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
        deliver(job, reply);
        cronJobRepository.markRun(job.getJobId(), now, CronSupport.nextRunAt(job.getCronExpr(), now));
    }

    private void deliver(CronJobRecord job, GatewayReply reply) throws Exception {
        String platformName = Utils.isNotEmpty(job.getDeliverPlatform()) ? job.getDeliverPlatform() : "local";
        if ("local".equalsIgnoreCase(platformName)) {
            return;
        }

        PlatformType platform = PlatformType.fromName(platformName);
        DeliveryRequest request = new DeliveryRequest();
        request.setPlatform(platform);
        request.setChatId(job.getDeliverChatId());
        request.setText(reply.getContent());
        deliveryService.deliver(request);
    }
}
