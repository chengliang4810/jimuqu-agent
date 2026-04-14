package com.jimuqu.claw.agent.job;

import com.jimuqu.claw.agent.runtime.model.JobRecord;
import com.jimuqu.claw.agent.runtime.model.JobStatus;
import com.jimuqu.claw.agent.store.JobStore;
import com.jimuqu.claw.config.ClawProperties;
import org.noear.solon.annotation.Destroy;
import org.noear.solon.annotation.Init;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JobSchedulerService {
    private static final Logger log = LoggerFactory.getLogger(JobSchedulerService.class);

    private final JobStore jobStore;
    private final JobExecutionService jobExecutionService;
    private final ClawProperties properties;
    private final AtomicBoolean polling = new AtomicBoolean(false);

    private ScheduledExecutorService executor;

    public JobSchedulerService(JobStore jobStore, JobExecutionService jobExecutionService, ClawProperties properties) {
        this.jobStore = jobStore;
        this.jobExecutionService = jobExecutionService;
        this.properties = properties;
    }

    @Init
    public void start() {
        if (executor != null) {
            return;
        }

        long intervalMs = resolvePollIntervalMs();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jimuqu-job-poller");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::safePollOnce, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Destroy
    public void stop() {
        if (executor == null) {
            return;
        }

        executor.shutdownNow();
        executor = null;
    }

    public void pollOnce() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }

        try {
            Instant now = Instant.now();
            for (JobRecord record : jobStore.list()) {
                pollRecord(record, now);
            }
        } finally {
            polling.set(false);
        }
    }

    private void safePollOnce() {
        try {
            pollOnce();
        } catch (Throwable e) {
            log.error("Job polling failed", e);
        }
    }

    private void pollRecord(JobRecord record, Instant now) {
        if (record == null || record.getStatus() != JobStatus.ACTIVE) {
            return;
        }

        if (record.getNextRunAt() == null) {
            Instant recovered = JobScheduleSupport.computeNextRunAt(record.getSchedule(), now);
            if (recovered != null) {
                record.setNextRunAt(recovered);
                record.setUpdatedAt(now);
                jobStore.save(record);
            }
            return;
        }

        if (record.getNextRunAt().isAfter(now)) {
            return;
        }

        try {
            jobExecutionService.triggerScheduled(record);
        } catch (RuntimeException e) {
            log.error("Scheduled job execution failed: {}", record.getJobId(), e);
        }
    }

    private long resolvePollIntervalMs() {
        Long configured = properties.getJobs().getPollIntervalMs();
        return configured == null || configured.longValue() <= 0L ? 1000L : configured.longValue();
    }
}
