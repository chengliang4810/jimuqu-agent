package com.jimuqu.solon.claw.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.pricing.PriceCatalog;
import com.jimuqu.solon.claw.pricing.UsageCostCalculator;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.storage.repository.SqliteUsageEventRepository;
import com.jimuqu.solon.claw.support.FixedSessionRepository;
import com.jimuqu.solon.claw.support.UnsupportedAgentRunRepository;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 验证用量回填在部分提交失败后的人工重跑与最终收敛语义。 */
class UsageBackfillServiceTest {
    /** 每个测试独占的临时工作目录。 */
    @TempDir Path tempDir;

    /** 写入前失败时，重跑应保留已提交前缀并补齐剩余运行与会话事件。 */
    @Test
    void rerunCompletesRemainingEventsAfterFailureBeforeInsert() throws Exception {
        SqliteDatabase database = new SqliteDatabase(testConfig("before-insert"));
        try {
            SqliteUsageEventRepository delegate = new SqliteUsageEventRepository(database);
            FailOnceUsageEventRepository usageRepository =
                    new FailOnceUsageEventRepository(delegate, "backfill-run-run-2", false);
            UsageBackfillService service =
                    new UsageBackfillService(
                            usageRepository,
                            new FixedAgentRunRepository(
                                    Arrays.asList(
                                            run("run-1", "session-1", 1000L),
                                            run("run-2", "session-2", 2000L))),
                            new FixedSessionRepository(
                                    Arrays.asList(
                                            session("session-1", 1000L),
                                            session("session-2", 2000L),
                                            session("session-3", 3000L))),
                            calculator());

            assertThatThrownBy(service::backfillApproximate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("simulated usage event failure");
            assertThat(delegate.findByEventId("backfill-run-run-1")).isNotNull();
            assertThat(delegate.findByEventId("backfill-run-run-2")).isNull();
            assertThat(delegate.findByEventId("backfill-session-session-3")).isNull();

            assertThat(service.backfillApproximate()).isEqualTo(2);
            assertThat(service.backfillApproximate()).isZero();

            List<UsageEventRecord> stored = delegate.listRecent(10);
            assertThat(stored)
                    .extracting(UsageEventRecord::getEventId)
                    .containsExactlyInAnyOrder(
                            "backfill-run-run-1",
                            "backfill-run-run-2",
                            "backfill-session-session-3");
            assertThat(stored)
                    .allSatisfy(
                            event -> {
                                assertThat(event.isBackfillApproximate()).isTrue();
                                assertThat(event.getRequestCount()).isEqualTo(1L);
                            });
            assertThat(delegate.findByEventId("backfill-session-session-1")).isNull();
            assertThat(delegate.findByEventId("backfill-session-session-2")).isNull();
        } finally {
            database.shutdown();
        }
    }

    /** 已提交写入才报错时，重跑不得复制已提交事件，并应继续处理后续会话。 */
    @Test
    void rerunConvergesAfterFailureReportedAfterCommit() throws Exception {
        SqliteDatabase database = new SqliteDatabase(testConfig("after-insert"));
        try {
            SqliteUsageEventRepository delegate = new SqliteUsageEventRepository(database);
            FailOnceUsageEventRepository usageRepository =
                    new FailOnceUsageEventRepository(delegate, "backfill-session-session-1", true);
            UsageBackfillService service =
                    new UsageBackfillService(
                            usageRepository,
                            new FixedAgentRunRepository(Collections.<AgentRunRecord>emptyList()),
                            new FixedSessionRepository(
                                    Arrays.asList(
                                            session("session-1", 1000L),
                                            session("session-2", 2000L))),
                            calculator());

            assertThatThrownBy(service::backfillApproximate)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("simulated usage event failure");
            assertThat(delegate.findByEventId("backfill-session-session-1")).isNotNull();
            assertThat(delegate.findByEventId("backfill-session-session-2")).isNull();

            assertThat(service.backfillApproximate()).isEqualTo(1);
            assertThat(service.backfillApproximate()).isZero();

            List<UsageEventRecord> stored = delegate.listRecent(10);
            assertThat(stored)
                    .extracting(UsageEventRecord::getEventId)
                    .containsExactlyInAnyOrder(
                            "backfill-session-session-1", "backfill-session-session-2");
            assertThat(stored)
                    .allSatisfy(
                            event -> {
                                assertThat(event.isBackfillApproximate()).isTrue();
                                assertThat(event.getRequestCount()).isEqualTo(1L);
                            });
        } finally {
            database.shutdown();
        }
    }

    /**
     * 创建测试数据库配置。
     *
     * @param caseName 测试场景名称。
     * @return 返回隔离的应用配置。
     */
    private AppConfig testConfig(String caseName) {
        Path home = tempDir.resolve(caseName);
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(home.toString());
        config.getRuntime().setStateDb(home.resolve("data/state.db").toString());
        return config;
    }

    /**
     * 创建固定有用量的成功运行。
     *
     * @param runId 运行标识。
     * @param sessionId 会话标识。
     * @param finishedAt 完成时间。
     * @return 返回运行记录。
     */
    private AgentRunRecord run(String runId, String sessionId, long finishedAt) {
        AgentRunRecord record = new AgentRunRecord();
        record.setRunId(runId);
        record.setSessionId(sessionId);
        record.setSourceKey("MEMORY:usage:" + sessionId);
        record.setStatus("success");
        record.setProvider("default");
        record.setModel("gpt-4o-mini");
        record.setInputTokens(10L);
        record.setOutputTokens(5L);
        record.setTotalTokens(15L);
        record.setStartedAt(finishedAt - 100L);
        record.setFinishedAt(finishedAt);
        return record;
    }

    /**
     * 创建固定有累计用量的会话。
     *
     * @param sessionId 会话标识。
     * @param lastUsageAt 最后用量时间。
     * @return 返回会话记录。
     */
    private SessionRecord session(String sessionId, long lastUsageAt) {
        SessionRecord record = new SessionRecord();
        record.setSessionId(sessionId);
        record.setSourceKey("MEMORY:usage:" + sessionId);
        record.setCumulativeInputTokens(10L);
        record.setCumulativeOutputTokens(5L);
        record.setCumulativeTotalTokens(15L);
        record.setLastResolvedProvider("default");
        record.setLastResolvedModel("gpt-4o-mini");
        record.setLastUsageAt(lastUsageAt);
        record.setCreatedAt(lastUsageAt - 100L);
        record.setUpdatedAt(lastUsageAt);
        return record;
    }

    /**
     * 创建使用内置价格目录的成本计算器。
     *
     * @return 返回成本计算器。
     */
    private UsageCostCalculator calculator() {
        return new UsageCostCalculator(PriceCatalog.builtinDefaults());
    }

    /** 固定返回已完成用量运行的测试仓储。 */
    private static final class FixedAgentRunRepository extends UnsupportedAgentRunRepository {
        /** 按服务读取顺序返回的运行记录。 */
        private final List<AgentRunRecord> runs;

        /**
         * 创建固定运行仓储。
         *
         * @param runs 已完成用量运行。
         */
        private FixedAgentRunRepository(List<AgentRunRecord> runs) {
            this.runs = runs;
        }

        /**
         * 返回固定运行记录。
         *
         * @param limit 最大返回数量。
         * @return 返回不超过限制的运行记录。
         */
        @Override
        public List<AgentRunRecord> listFinishedWithUsage(int limit) {
            return runs.subList(0, Math.min(Math.max(0, limit), runs.size()));
        }
    }

    /** 对真实用量仓储注入一次写入前或写入后失败的测试装饰器。 */
    private static final class FailOnceUsageEventRepository implements UsageEventRepository {
        /** 真实 SQLite 用量事件仓储。 */
        private final UsageEventRepository delegate;

        /** 触发一次性失败的事件标识。 */
        private final String failingEventId;

        /** 是否在真实写入提交后再向调用方报告失败。 */
        private final boolean failAfterDelegate;

        /** 一次性失败是否已经触发。 */
        private boolean failed;

        /**
         * 创建一次性失败装饰器。
         *
         * @param delegate 真实用量事件仓储。
         * @param failingEventId 触发失败的事件标识。
         * @param failAfterDelegate 是否在委托写入后失败。
         */
        private FailOnceUsageEventRepository(
                UsageEventRepository delegate, String failingEventId, boolean failAfterDelegate) {
            this.delegate = delegate;
            this.failingEventId = failingEventId;
            this.failAfterDelegate = failAfterDelegate;
        }

        /**
         * 委托写入，并在目标事件首次出现时注入指定位置的异常。
         *
         * @param record 用量事件。
         * @return 返回真实仓储是否插入。
         * @throws Exception 委托失败或一次性模拟失败时抛出。
         */
        @Override
        public boolean insertIfAbsent(UsageEventRecord record) throws Exception {
            boolean shouldFail =
                    !failed && record != null && failingEventId.equals(record.getEventId());
            if (shouldFail && !failAfterDelegate) {
                failed = true;
                throw simulatedFailure();
            }
            boolean inserted = delegate.insertIfAbsent(record);
            if (shouldFail) {
                failed = true;
                throw simulatedFailure();
            }
            return inserted;
        }

        /**
         * 委托按事件标识查询。
         *
         * @param eventId 事件标识。
         * @return 返回用量事件。
         * @throws Exception 查询失败时抛出。
         */
        @Override
        public UsageEventRecord findByEventId(String eventId) throws Exception {
            return delegate.findByEventId(eventId);
        }

        /**
         * 委托最近事件查询。
         *
         * @param limit 最大返回数量。
         * @return 返回最近事件。
         * @throws Exception 查询失败时抛出。
         */
        @Override
        public List<UsageEventRecord> listRecent(int limit) throws Exception {
            return delegate.listRecent(limit);
        }

        /**
         * 委托时间范围事件查询。
         *
         * @param fromInclusive 起始时间。
         * @param toInclusive 截止时间。
         * @param limit 最大返回数量。
         * @return 返回匹配事件。
         * @throws Exception 查询失败时抛出。
         */
        @Override
        public List<UsageEventRecord> listBetween(long fromInclusive, long toInclusive, int limit)
                throws Exception {
            return delegate.listBetween(fromInclusive, toInclusive, limit);
        }

        /**
         * 创建稳定的一次性模拟数据库异常。
         *
         * @return 返回模拟异常。
         */
        private SQLException simulatedFailure() {
            return new SQLException("simulated usage event failure");
        }
    }
}
