package com.jimuqu.claw.agent.job;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.JobRecord;
import com.jimuqu.claw.agent.runtime.model.JobStatus;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.store.JobStore;
import com.jimuqu.claw.agent.store.file.FileJobStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;
import com.jimuqu.claw.config.ClawProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;

public class JobSchedulerServiceTest {
    @Test
    public void pollOnceExecutesDueActiveJobsAndSkipsPausedJobs() {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/job-poller-due")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            JobStore jobStore = new FileJobStore(layout);

            Instant now = Instant.now();
            jobStore.save(JobRecord.builder()
                    .jobId("job-active")
                    .name("active")
                    .prompt("active prompt")
                    .schedule("10s")
                    .workspaceRoot(root.toString())
                    .status(JobStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .nextRunAt(now.minusSeconds(1))
                    .build());
            jobStore.save(JobRecord.builder()
                    .jobId("job-paused")
                    .name("paused")
                    .prompt("paused prompt")
                    .schedule("10s")
                    .workspaceRoot(root.toString())
                    .status(JobStatus.PAUSED)
                    .createdAt(now)
                    .updatedAt(now)
                    .nextRunAt(now.minusSeconds(1))
                    .build());

            CapturingRuntimeService runtimeService = new CapturingRuntimeService();
            JobExecutionService executionService = new JobExecutionService(jobStore, layout, () -> runtimeService);
            JobSchedulerService schedulerService = new JobSchedulerService(jobStore, executionService, new ClawProperties());

            schedulerService.pollOnce();

            Assertions.assertEquals(1, runtimeService.callCount);
            Assertions.assertEquals("cronjob", runtimeService.capturedRequest.getSource());
            Assertions.assertEquals("job:job-active", runtimeService.capturedRequest.getSessionContext().getUserId());

            JobRecord active = jobStore.get("job-active");
            Assertions.assertNotNull(active.getLastRunAt());
            Assertions.assertNotNull(active.getNextRunAt());
            Assertions.assertTrue(active.getNextRunAt().isAfter(active.getLastRunAt()));
            Assertions.assertTrue(String.valueOf(active.getLastResultSummary()).contains("scheduled-complete"));

            JobRecord paused = jobStore.get("job-paused");
            Assertions.assertNull(paused.getLastRunAt());
        } finally {
            deleteTree(root);
        }
    }

    @Test
    public void pollOnceRecoversNextRunAtForActiveJobsMissingScheduleState() {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/job-poller-recover")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();
            JobStore jobStore = new FileJobStore(layout);

            Instant now = Instant.now();
            jobStore.save(JobRecord.builder()
                    .jobId("job-recover")
                    .name("recover")
                    .prompt("recover prompt")
                    .schedule("5s")
                    .workspaceRoot(root.toString())
                    .status(JobStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .nextRunAt(null)
                    .build());

            CapturingRuntimeService runtimeService = new CapturingRuntimeService();
            JobExecutionService executionService = new JobExecutionService(jobStore, layout, () -> runtimeService);
            JobSchedulerService schedulerService = new JobSchedulerService(jobStore, executionService, new ClawProperties());

            schedulerService.pollOnce();

            Assertions.assertEquals(0, runtimeService.callCount);
            JobRecord recovered = jobStore.get("job-recover");
            Assertions.assertNotNull(recovered.getNextRunAt());
            Assertions.assertTrue(recovered.getNextRunAt().isAfter(now));
        } finally {
            deleteTree(root);
        }
    }

    private void deleteTree(Path root) {
        if (root == null) {
            return;
        }

        try {
            Files.walk(root)
                    .sorted(Collections.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static class CapturingRuntimeService implements RuntimeService {
        private int callCount;
        private RunRequest capturedRequest;

        @Override
        public RunRecord handleInbound(ChannelInboundMessage inboundMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunRecord handleRequest(RunRequest request) {
            this.callCount++;
            this.capturedRequest = request;
            return RunRecord.builder()
                    .runId("run-job-" + callCount)
                    .sessionId(request.getSessionContext().getSessionId())
                    .status(RunStatus.SUCCEEDED)
                    .responseText("scheduled-complete")
                    .createdAt(request.getCreatedAt())
                    .build();
        }
    }
}
