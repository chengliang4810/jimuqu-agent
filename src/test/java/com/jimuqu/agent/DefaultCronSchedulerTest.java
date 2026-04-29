package com.jimuqu.agent;

import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.CronJobRecord;
import com.jimuqu.agent.scheduler.DefaultCronScheduler;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultCronSchedulerTest {
    @Test
    void shouldAdvanceBeforeRunAndDeliverToHomeChannelFallback() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.send("admin-dm", "admin-user", "hello");
        env.send("admin-dm", "admin-user", "/pairing claim-admin");
        env.gatewayService.handle(env.message("home-room", "admin-user", "group", "Home", "Admin", "/sethome"));

        CronJobRecord job = job("job-1", "MEMORY:admin-dm:admin-user");
        job.setDeliverPlatform("local");
        env.cronJobRepository.save(job);

        DefaultCronScheduler scheduler = new DefaultCronScheduler(
                env.appConfig,
                env.cronJobRepository,
                env.conversationOrchestrator,
                env.deliveryService,
                env.gatewayPolicyRepository
        );
        scheduler.tick();

        CronJobRecord updated = env.cronJobRepository.findById("job-1");
        assertThat(updated.getLastRunAt()).isGreaterThan(0L);
        assertThat(updated.getNextRunAt()).isGreaterThan(updated.getLastRunAt());
        assertThat(env.memoryChannelAdapter.getLastRequest().getPlatform()).isEqualTo(PlatformType.MEMORY);
        assertThat(env.memoryChannelAdapter.getLastRequest().getChatId()).isEqualTo("home-room");
        assertThat(env.memoryChannelAdapter.getLastRequest().getText()).contains("echo:scheduled prompt");
    }

    private CronJobRecord job(String id, String sourceKey) {
        long now = System.currentTimeMillis();
        CronJobRecord job = new CronJobRecord();
        job.setJobId(id);
        job.setName(id);
        job.setCronExpr("* * * * *");
        job.setPrompt("scheduled prompt");
        job.setSourceKey(sourceKey);
        job.setDeliverPlatform("local");
        job.setStatus("ACTIVE");
        job.setNextRunAt(now - 1000L);
        job.setLastRunAt(0L);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return job;
    }
}
