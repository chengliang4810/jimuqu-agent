package com.jimuqu.claw.agent.job;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.JobRecord;
import com.jimuqu.claw.agent.runtime.model.JobStatus;
import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.agent.store.JobStore;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.channel.ReplyRouteSupport;
import com.jimuqu.claw.support.Ids;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class JobExecutionService {
    private final JobStore jobStore;
    private final WorkspaceLayout workspaceLayout;
    private final RuntimeServiceResolver runtimeServiceResolver;
    private final ConcurrentHashMap<String, ReentrantLock> jobLocks = new ConcurrentHashMap<String, ReentrantLock>();

    public JobExecutionService(JobStore jobStore, WorkspaceLayout workspaceLayout, RuntimeServiceResolver runtimeServiceResolver) {
        this.jobStore = jobStore;
        this.workspaceLayout = workspaceLayout;
        this.runtimeServiceResolver = runtimeServiceResolver;
    }

    public RunRecord triggerNow(String jobId, String triggerReason) {
        JobRecord record = jobStore.get(jobId);
        if (record == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }

        return triggerNow(record, triggerReason);
    }

    public RunRecord triggerNow(JobRecord record, String triggerReason) {
        return trigger(record, StrUtil.blankToDefault(triggerReason, "Triggered manually."), false);
    }

    public RunRecord triggerScheduled(JobRecord record) {
        return trigger(record, "Scheduled trigger.", true);
    }

    private RunRecord trigger(JobRecord snapshot, String triggerReason, boolean scheduled) {
        if (snapshot == null || StrUtil.isBlank(snapshot.getJobId())) {
            throw new IllegalArgumentException("Job record is required");
        }

        ReentrantLock lock = jobLocks.computeIfAbsent(snapshot.getJobId(), key -> new ReentrantLock());
        lock.lock();
        try {
            JobRecord record = jobStore.get(snapshot.getJobId());
            if (record == null) {
                throw new IllegalArgumentException("Job not found: " + snapshot.getJobId());
            }

            Instant now = Instant.now();
            if (scheduled) {
                if (record.getStatus() != JobStatus.ACTIVE) {
                    return null;
                }
                if (record.getNextRunAt() == null || record.getNextRunAt().isAfter(now)) {
                    return null;
                }
            }

            RuntimeService runtimeService = runtimeServiceResolver == null ? null : runtimeServiceResolver.resolve();
            if (runtimeService == null) {
                throw new IllegalStateException("Runtime service is unavailable");
            }

            record.setLastRunAt(now);
            record.setUpdatedAt(now);
            record.setLastResultSummary(triggerReason);
            record.setNextRunAt(record.getStatus() == JobStatus.PAUSED
                    ? null
                    : JobScheduleSupport.computeNextRunAt(record.getSchedule(), now));
            jobStore.save(record);

            try {
                RunRecord runRecord = runtimeService.handleRequest(buildRunRequest(record, triggerReason, scheduled, now));
                record.setUpdatedAt(Instant.now());
                record.setLastResultSummary(buildRunSummary(runRecord));
                jobStore.save(record);
                return runRecord;
            } catch (RuntimeException e) {
                record.setUpdatedAt(Instant.now());
                record.setLastResultSummary(buildFailureSummary(e));
                jobStore.save(record);
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    private RunRequest buildRunRequest(JobRecord record, String triggerReason, boolean scheduled, Instant createdAt) {
        ReplyRoute replyRoute = ReplyRouteSupport.parse(record.getDeliverTarget());
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("jobId", record.getJobId());
        metadata.put("jobName", record.getName());
        metadata.put("trigger", scheduled ? "scheduled" : "manual");
        metadata.put("triggerReason", triggerReason);

        SessionContext sessionContext = SessionContext.builder()
                .sessionId(buildSessionId(record))
                .platform(resolvePlatform(replyRoute))
                .chatId(replyRoute == null ? null : replyRoute.getChatId())
                .threadId(replyRoute == null ? null : replyRoute.getThreadId())
                .userId("job:" + record.getJobId())
                .workspaceRoot(StrUtil.blankToDefault(record.getWorkspaceRoot(), workspaceLayout.getRoot().toString()))
                .metadata(metadata)
                .build();

        List<String> skillNames = record.getSkillNames() == null
                ? new ArrayList<String>()
                : new ArrayList<String>(record.getSkillNames());

        return RunRequest.builder()
                .sessionContext(sessionContext)
                .replyRoute(replyRoute)
                .userMessage(buildPrompt(record, skillNames))
                .modelAlias(record.getModelAlias())
                .source("cronjob")
                .skillNames(skillNames)
                .createdAt(createdAt)
                .build();
    }

    private String buildPrompt(JobRecord record, List<String> skillNames) {
        String prompt = StrUtil.nullToDefault(record.getPrompt(), "").trim();
        if (skillNames == null || skillNames.isEmpty()) {
            return prompt;
        }

        StringBuilder buffer = new StringBuilder();
        if (StrUtil.isNotBlank(prompt)) {
            buffer.append(prompt);
            buffer.append("\n\n");
        }
        buffer.append("Attached skills: ");
        buffer.append(StrUtil.join(", ", skillNames));
        return buffer.toString();
    }

    private String buildSessionId(JobRecord record) {
        return "sess_" + Ids.hashKey("cronjob|" + record.getJobId());
    }

    private String resolvePlatform(ReplyRoute replyRoute) {
        if (replyRoute == null || StrUtil.isBlank(replyRoute.getPlatform())) {
            return "cronjob";
        }
        return replyRoute.getPlatform();
    }

    private String buildRunSummary(RunRecord runRecord) {
        if (runRecord == null) {
            return "Run completed.";
        }

        String summary = StrUtil.blankToDefault(runRecord.getResponseText(), runRecord.getErrorMessage());
        if (StrUtil.isBlank(summary)) {
            RunStatus status = runRecord.getStatus();
            summary = status == null ? "completed" : status.name().toLowerCase();
        }

        return StrUtil.maxLength(summary, 400);
    }

    private String buildFailureSummary(RuntimeException e) {
        String message = e.getMessage();
        if (StrUtil.isBlank(message)) {
            message = e.getClass().getSimpleName();
        }
        return StrUtil.maxLength("Job execution failed: " + message, 400);
    }

    public interface RuntimeServiceResolver {
        RuntimeService resolve();
    }
}
