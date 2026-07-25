package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.context.SkillCuratorService;
import com.jimuqu.solon.claw.core.model.AgentRunStopResult;
import com.jimuqu.solon.claw.core.service.AgentRunControlService;
import com.jimuqu.solon.claw.scheduler.SkillCuratorScheduler;
import com.jimuqu.solon.claw.support.TestEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 技能维护调度器的前台空闲门禁与跳过报告测试。 */
class SkillCuratorSchedulerTest {
    /** 后台运行刚结束也不得阻止首次技能维护检查。 */
    @Test
    void shouldIgnoreBackgroundCompletionForIdleWindow() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getCurator().setEnabled(true);
        env.appConfig.getCurator().setMinIdleHours(2.0D);
        SkillCuratorService service = new SkillCuratorService(env.appConfig, env.localSkillService);
        List<Map<String, Object>> reports = new ArrayList<Map<String, Object>>();
        service.setReportSink(reports::add);
        SkillCuratorScheduler scheduler =
                new SkillCuratorScheduler(
                        env.appConfig,
                        service,
                        new FixedRunControl(false, System.currentTimeMillis(), 0L));

        scheduler.tick();

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0)).containsEntry("status", "ok");
    }

    /** 最近前台对话不足空闲阈值时必须记录可查询的 idle_wait 报告。 */
    @Test
    void shouldReportRecentForegroundIdleWait() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getCurator().setEnabled(true);
        env.appConfig.getCurator().setMinIdleHours(2.0D);
        SkillCuratorService service = new SkillCuratorService(env.appConfig, env.localSkillService);
        List<Map<String, Object>> reports = new ArrayList<Map<String, Object>>();
        service.setReportSink(reports::add);
        long foregroundFinishedAt = System.currentTimeMillis();
        SkillCuratorScheduler scheduler =
                new SkillCuratorScheduler(
                        env.appConfig,
                        service,
                        new FixedRunControl(false, foregroundFinishedAt, foregroundFinishedAt));

        scheduler.tick();

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0))
                .containsEntry("status", "idle_wait")
                .containsEntry("reason", "minimum_idle")
                .containsEntry("lastForegroundRunFinishedAt", foregroundFinishedAt);
        assertThat(((Number) reports.get(0).get("remainingIdleMillis")).longValue()).isPositive();
        assertThat(service.status().get("lastRunAt")).isEqualTo(0L);
    }

    /** 活跃任务占用 Agent 时必须记录 active_run，而不是无痕返回。 */
    @Test
    void shouldReportActiveRunWait() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.appConfig.getCurator().setEnabled(true);
        SkillCuratorService service = new SkillCuratorService(env.appConfig, env.localSkillService);
        List<Map<String, Object>> reports = new ArrayList<Map<String, Object>>();
        service.setReportSink(reports::add);
        SkillCuratorScheduler scheduler =
                new SkillCuratorScheduler(
                        env.appConfig, service, new FixedRunControl(true, 0L, 0L));

        scheduler.tick();

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0))
                .containsEntry("status", "idle_wait")
                .containsEntry("reason", "active_run")
                .containsEntry("runningRunCount", 1);
    }

    /** 固定运行状态的测试替身，分别暴露全局和前台完成时间。 */
    private static final class FixedRunControl implements AgentRunControlService {
        /** 是否存在运行中的 Agent。 */
        private final boolean busy;

        /** 最近任意运行完成时间。 */
        private final long lastRunFinishedAt;

        /** 最近前台对话完成时间。 */
        private final long lastForegroundRunFinishedAt;

        /**
         * 创建固定运行状态。
         *
         * @param busy 是否存在运行中的 Agent。
         * @param lastRunFinishedAt 最近任意运行完成时间。
         * @param lastForegroundRunFinishedAt 最近前台对话完成时间。
         */
        private FixedRunControl(
                boolean busy, long lastRunFinishedAt, long lastForegroundRunFinishedAt) {
            this.busy = busy;
            this.lastRunFinishedAt = lastRunFinishedAt;
            this.lastForegroundRunFinishedAt = lastForegroundRunFinishedAt;
        }

        /** 测试不停止运行。 */
        @Override
        public AgentRunStopResult stop(String sourceKey) {
            return AgentRunStopResult.none();
        }

        /** 返回固定单来源运行状态。 */
        @Override
        public boolean isRunning(String sourceKey) {
            return busy;
        }

        /** 返回固定全局运行状态。 */
        @Override
        public boolean hasRunningRuns() {
            return busy;
        }

        /** 返回固定运行数量。 */
        @Override
        public int runningRunCount() {
            return busy ? 1 : 0;
        }

        /** 返回最近任意运行完成时间。 */
        @Override
        public long lastRunFinishedAt() {
            return lastRunFinishedAt;
        }

        /** 返回最近前台用户对话完成时间。 */
        @Override
        public long lastForegroundRunFinishedAt() {
            return lastForegroundRunFinishedAt;
        }
    }
}
