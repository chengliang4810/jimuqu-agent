package com.jimuqu.solon.claw.usage;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.pricing.UsageCostCalculator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 提供用量Backfill相关业务能力，封装调用方不需要感知的运行细节。 */
public class UsageBackfillService {
    /** 单页最多读取一万条运行，避免回填期间一次加载无界历史数据。 */
    private static final int RUN_PAGE_SIZE = 10000;

    /** 保存用量事件仓储依赖，用于访问持久化数据。 */
    private final UsageEventRepository usageEventRepository;

    /** 保存Agent运行仓储依赖，用于访问持久化数据。 */
    private final AgentRunRepository agentRunRepository;

    /** 保存会话仓储依赖，用于访问持久化数据。 */
    private final SessionRepository sessionRepository;

    /** 记录用量Backfill中的calculator。 */
    private final UsageCostCalculator calculator;

    /**
     * 创建用量Backfill服务实例，并注入运行所需依赖。
     *
     * @param usageEventRepository 用量事件仓储依赖。
     * @param agentRunRepository Agent运行仓储依赖。
     * @param sessionRepository 会话仓储依赖。
     * @param calculator calculator 参数。
     */
    public UsageBackfillService(
            UsageEventRepository usageEventRepository,
            AgentRunRepository agentRunRepository,
            SessionRepository sessionRepository,
            UsageCostCalculator calculator) {
        this.usageEventRepository = usageEventRepository;
        this.agentRunRepository = agentRunRepository;
        this.sessionRepository = sessionRepository;
        this.calculator = calculator;
    }

    /**
     * 执行backfillApproximate相关逻辑。
     *
     * @return 返回backfill Approximate结果。
     */
    public int backfillApproximate() throws Exception {
        int inserted = 0;
        Set<String> sessionsWithRunUsage = new LinkedHashSet<String>();
        long beforeFinishedAt = -1L;
        String beforeRunId = null;
        while (true) {
            List<AgentRunRecord> runs =
                    agentRunRepository.listFinishedWithUsage(
                            beforeFinishedAt, beforeRunId, RUN_PAGE_SIZE);
            if (runs == null || runs.isEmpty()) {
                break;
            }
            for (AgentRunRecord run : runs) {
                UsageEventRecord event = fromRun(run);
                if (event != null) {
                    if (StrUtil.isNotBlank(event.getSessionId())) {
                        sessionsWithRunUsage.add(event.getSessionId());
                    }
                    if (usageEventRepository.insertIfAbsent(event)) {
                        inserted++;
                    }
                }
            }
            AgentRunRecord last = runs.get(runs.size() - 1);
            requireAdvancedCursor(beforeFinishedAt, beforeRunId, last);
            beforeFinishedAt = last.getFinishedAt();
            beforeRunId = last.getRunId();
        }
        int sessionCount = sessionRepository.countAll();
        List<SessionRecord> sessions = sessionRepository.listRecent(sessionCount);
        for (SessionRecord session : sessions) {
            if (session != null && sessionsWithRunUsage.contains(session.getSessionId())) {
                continue;
            }
            UsageEventRecord event = fromSession(session);
            if (event != null && usageEventRepository.insertIfAbsent(event)) {
                inserted++;
            }
        }
        return inserted;
    }

    /**
     * 校验仓储返回的末条记录能够推进键集分页游标，避免错误实现导致无限循环。
     *
     * @param previousFinishedAt 上一页完成时间游标。
     * @param previousRunId 上一页运行标识游标。
     * @param last 当前页末条运行。
     */
    private void requireAdvancedCursor(
            long previousFinishedAt, String previousRunId, AgentRunRecord last) {
        if (last == null || last.getFinishedAt() < 0L || StrUtil.isBlank(last.getRunId())) {
            throw new IllegalStateException("用量运行分页返回了无效游标");
        }
        if (previousFinishedAt < 0L) {
            return;
        }
        boolean advanced =
                last.getFinishedAt() < previousFinishedAt
                        || (last.getFinishedAt() == previousFinishedAt
                                && last.getRunId().compareTo(previousRunId) < 0);
        if (!advanced) {
            throw new IllegalStateException("用量运行分页游标未前进");
        }
    }

    /**
     * 从输入转换运行。
     *
     * @param run 运行参数。
     * @return 返回运行结果。
     */
    private UsageEventRecord fromRun(AgentRunRecord run) {
        if (run == null || StrUtil.isBlank(run.getRunId())) {
            return null;
        }
        long input = Math.max(0L, run.getInputTokens());
        long output = Math.max(0L, run.getOutputTokens());
        long total = Math.max(input + output, run.getTotalTokens());
        if (total <= 0) {
            return null;
        }
        UsageEventRecord event = base("backfill-run-" + run.getRunId());
        event.setRunId(run.getRunId());
        event.setSessionId(run.getSessionId());
        event.setSourceKey(run.getSourceKey());
        event.setProvider(run.getProvider());
        event.setModel(run.getModel());
        event.setInputTokens(input);
        event.setOutputTokens(output);
        event.setTotalTokens(total);
        event.setRequestCount(1L);
        event.setCreatedAt(run.getFinishedAt() > 0 ? run.getFinishedAt() : run.getStartedAt());
        applyCost(event);
        return event;
    }

    /**
     * 从输入转换会话。
     *
     * @param session 会话参数。
     * @return 返回会话结果。
     */
    private UsageEventRecord fromSession(SessionRecord session) {
        if (session == null || StrUtil.isBlank(session.getSessionId())) {
            return null;
        }
        long total = Math.max(0L, session.getCumulativeTotalTokens());
        if (total <= 0) {
            return null;
        }
        UsageEventRecord event = base("backfill-session-" + session.getSessionId());
        event.setSessionId(session.getSessionId());
        event.setSourceKey(session.getSourceKey());
        event.setProvider(session.getLastResolvedProvider());
        event.setModel(
                StrUtil.blankToDefault(session.getLastResolvedModel(), session.getModelOverride()));
        event.setInputTokens(Math.max(0L, session.getCumulativeInputTokens()));
        event.setOutputTokens(Math.max(0L, session.getCumulativeOutputTokens()));
        event.setCacheReadTokens(Math.max(0L, session.getCumulativeCacheReadTokens()));
        event.setCacheWriteTokens(Math.max(0L, session.getCumulativeCacheWriteTokens()));
        event.setReasoningTokens(Math.max(0L, session.getCumulativeReasoningTokens()));
        event.setTotalTokens(total);
        event.setRequestCount(1L);
        event.setCreatedAt(
                session.getLastUsageAt() > 0
                        ? session.getLastUsageAt()
                        : Math.max(session.getCreatedAt(), session.getUpdatedAt()));
        applyCost(event);
        return event;
    }

    /**
     * 执行基础相关逻辑。
     *
     * @param eventId 事件标识。
     * @return 返回base结果。
     */
    private UsageEventRecord base(String eventId) {
        UsageEventRecord event = new UsageEventRecord();
        event.setEventId(eventId);
        event.setBackfillApproximate(true);
        return event;
    }

    /**
     * 应用成本。
     *
     * @param event 事件参数。
     */
    private void applyCost(UsageEventRecord event) {
        UsageEventCostSupport.apply(event, UsageEventCostSupport.calculate(calculator, event));
    }
}
