package com.jimuqu.agent.tool.runtime;

import com.jimuqu.agent.core.model.CronJobRecord;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.support.CronSupport;
import com.jimuqu.agent.support.IdSupport;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;

import java.util.List;

/**
 * CronjobTools 实现。
 */
public class CronjobTools {
    private final CronJobRepository cronJobRepository;
    private final String sourceKey;

    public CronjobTools(CronJobRepository cronJobRepository, String sourceKey) {
        this.cronJobRepository = cronJobRepository;
        this.sourceKey = sourceKey;
    }

    @ToolMapping(name = "cronjob", description = "Manage cron jobs. action can be create, list, pause, resume, or delete.")
    public String cronjob(@Param(name = "action", description = "create、list、pause、resume、delete") String action,
                          @Param(name = "name", description = "任务名或任务 ID", required = false) String name,
                          @Param(name = "cronExpr", description = "cron 表达式", required = false) String cronExpr,
                          @Param(name = "prompt", description = "任务提示词", required = false) String prompt) throws Exception {
        if ("list".equalsIgnoreCase(action)) {
            List<CronJobRecord> jobs = cronJobRepository.listBySource(sourceKey);
            StringBuilder buffer = new StringBuilder();
            for (CronJobRecord job : jobs) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(job.getJobId()).append(" ").append(job.getName()).append(" ").append(job.getStatus());
            }
            return buffer.length() == 0 ? "No cron jobs" : buffer.toString();
        }

        if ("create".equalsIgnoreCase(action)) {
            long now = System.currentTimeMillis();
            CronJobRecord record = new CronJobRecord();
            record.setJobId(IdSupport.newId());
            record.setName(name);
            record.setCronExpr(cronExpr);
            record.setPrompt(prompt);
            record.setSourceKey(sourceKey);
            record.setStatus("ACTIVE");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            record.setNextRunAt(CronSupport.nextRunAt(cronExpr, now));
            cronJobRepository.save(record);
            return "Created cron job: " + record.getJobId();
        }

        if ("pause".equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(name, "PAUSED");
            return "Paused cron job: " + name;
        }

        if ("resume".equalsIgnoreCase(action)) {
            cronJobRepository.updateStatus(name, "ACTIVE");
            return "Resumed cron job: " + name;
        }

        if ("delete".equalsIgnoreCase(action)) {
            cronJobRepository.delete(name);
            return "Deleted cron job: " + name;
        }

        return "Unsupported cronjob action";
    }
}
