package com.jimuqu.agent.core;

import java.util.List;

public interface CronJobRepository {
    CronJobRecord save(CronJobRecord job) throws Exception;

    CronJobRecord findById(String jobId) throws Exception;

    List<CronJobRecord> listBySource(String sourceKey) throws Exception;

    List<CronJobRecord> listDue(long nowEpochMillis) throws Exception;

    void delete(String jobId) throws Exception;

    void updateStatus(String jobId, String status) throws Exception;

    void markRun(String jobId, long lastRunAt, long nextRunAt) throws Exception;
}
