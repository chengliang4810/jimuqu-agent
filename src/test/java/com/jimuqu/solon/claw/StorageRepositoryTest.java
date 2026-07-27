package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.core.model.AgentRunRecord;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.ToolCallRecord;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.storage.session.SqliteAgentSession;
import com.jimuqu.solon.claw.support.TestEnvironment;
import com.jimuqu.solon.claw.tool.runtime.DangerousCommandApprovalService;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;

public class StorageRepositoryTest {
    @Test
    void shouldPersistAndSearchSessions() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:room-a:user-a");
        session.setNdjson("hello world");
        session.setTitle("alpha session");
        session.setCompressedSummary("beta summary");
        session.setLastInputTokens(12);
        session.setLastOutputTokens(8);
        session.setLastCacheReadTokens(3);
        session.setLastCacheWriteTokens(2);
        session.setLastTotalTokens(25);
        session.setCumulativeInputTokens(42);
        session.setCumulativeOutputTokens(18);
        session.setCumulativeCacheReadTokens(5);
        session.setCumulativeCacheWriteTokens(4);
        session.setCumulativeTotalTokens(69);
        session.setLastResolvedProvider("openai-responses");
        session.setLastResolvedModel("gpt-5.4");
        session.setPlatformMessageId("pm-storage-1");
        session.setMetadataJson("{\"topic\":\"canonical\"}");
        env.sessionRepository.save(session);

        SessionRecord stored = env.sessionRepository.findById(session.getSessionId());
        assertThat(stored).isNotNull();
        assertThat(stored.getLastInputTokens()).isEqualTo(12);
        assertThat(stored.getLastOutputTokens()).isEqualTo(8);
        assertThat(stored.getLastCacheReadTokens()).isEqualTo(3);
        assertThat(stored.getLastCacheWriteTokens()).isEqualTo(2);
        assertThat(stored.getLastTotalTokens()).isEqualTo(25);
        assertThat(stored.getCumulativeInputTokens()).isEqualTo(42);
        assertThat(stored.getCumulativeOutputTokens()).isEqualTo(18);
        assertThat(stored.getCumulativeCacheReadTokens()).isEqualTo(5);
        assertThat(stored.getCumulativeCacheWriteTokens()).isEqualTo(4);
        assertThat(stored.getCumulativeTotalTokens()).isEqualTo(69);
        assertThat(stored.getLastResolvedProvider()).isEqualTo("openai-responses");
        assertThat(stored.getLastResolvedModel()).isEqualTo("gpt-5.4");
        assertThat(stored.getPlatformMessageId()).isEqualTo("pm-storage-1");
        assertThat(stored.getMetadataJson()).contains("canonical");
        assertThat(env.sessionRepository.search("hello", 10)).hasSize(1);
        assertThat(env.sessionRepository.search("alpha", 10)).hasSize(1);
        assertThat(env.sessionRepository.search("beta", 10)).hasSize(1);

        SessionRecord clone =
                env.sessionRepository.cloneSession(
                        "MEMORY:room-a:user-a", session.getSessionId(), "review");
        assertThat(clone.getParentSessionId()).isEqualTo(session.getSessionId());
        assertThat(env.sessionRepository.findBySourceAndBranch("MEMORY:room-a:user-a", "review"))
                .isNotNull();
    }

    /** 旧运行快照不得覆盖保存期间由定向接口更新的会话设置与状态。 */
    @Test
    void shouldPreserveConcurrentSessionSettingsWhenSavingStaleSnapshot() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord created =
                env.sessionRepository.bindNewSession("MEMORY:concurrent-settings:user");
        SessionRecord firstStale = env.sessionRepository.findById(created.getSessionId());
        SessionRecord secondStale = env.sessionRepository.findById(created.getSessionId());

        env.sessionRepository.setModelOverride(created.getSessionId(), "openai:gpt-5.4");
        env.sessionRepository.setServiceTierOverride(created.getSessionId(), "priority");
        env.sessionRepository.setReasoningEffortOverride(created.getSessionId(), "high");
        env.sessionRepository.setGoalState(created.getSessionId(), "{\"goal\":\"active\"}");
        env.sessionRepository.setLastLearningAt(created.getSessionId(), 42L);
        firstStale.setNdjson("runtime result");
        firstStale.setLastInputTokens(12L);
        env.sessionRepository.save(firstStale);

        firstStale.setNdjson("second runtime result");
        env.sessionRepository.save(firstStale);
        secondStale.setNdjson("third runtime result");
        secondStale.setLastInputTokens(12L);
        env.sessionRepository.save(secondStale);

        SessionRecord stored = env.sessionRepository.findById(created.getSessionId());
        assertThat(stored.getModelOverride()).isEqualTo("openai:gpt-5.4");
        assertThat(stored.getServiceTierOverride()).isEqualTo("priority");
        assertThat(stored.getReasoningEffortOverride()).isEqualTo("high");
        assertThat(stored.getGoalStateJson()).isEqualTo("{\"goal\":\"active\"}");
        assertThat(stored.getLastLearningAt()).isEqualTo(42L);
        assertThat(stored.getNdjson()).isEqualTo("third runtime result");
        assertThat(stored.getLastInputTokens()).isEqualTo(12L);
    }

    /** 无并发更新时，已有会话的全量保存仍应写入设置与目标状态。 */
    @Test
    void shouldSaveExistingSessionSettingsWithoutConcurrentChange() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord current =
                env.sessionRepository.bindNewSession("MEMORY:existing-settings:user");
        current.setModelOverride("openai:gpt-5.4");
        current.setServiceTierOverride("priority");
        current.setReasoningEffortOverride("high");
        current.setGoalStateJson("{\"goal\":\"active\"}");
        current.setLastLearningAt(42L);

        env.sessionRepository.save(current);

        SessionRecord stored = env.sessionRepository.findById(current.getSessionId());
        assertThat(stored.getModelOverride()).isEqualTo("openai:gpt-5.4");
        assertThat(stored.getServiceTierOverride()).isEqualTo("priority");
        assertThat(stored.getReasoningEffortOverride()).isEqualTo("high");
        assertThat(stored.getGoalStateJson()).isEqualTo("{\"goal\":\"active\"}");
        assertThat(stored.getLastLearningAt()).isEqualTo(42L);
    }

    /** 无并发冲突时，全量保存必须保留调用方显式指定的旧更新时间。 */
    @Test
    void shouldPreserveExplicitOldUpdatedAtOnFullSave() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord current =
                env.sessionRepository.bindNewSession("MEMORY:explicit-old-time:user");
        current.setGoalStateJson("{\"goal\":\"historical\"}");
        current.setUpdatedAt(123L);

        env.sessionRepository.save(current);

        SessionRecord stored = env.sessionRepository.findById(current.getSessionId());
        assertThat(stored.getGoalStateJson()).isEqualTo("{\"goal\":\"historical\"}");
        assertThat(stored.getUpdatedAt()).isEqualTo(123L);
    }

    /** 更新时间回退到旧快照版本后，并发设置仍不得被旧快照覆盖。 */
    @Test
    void shouldPreserveConcurrentSettingsAfterUpdatedAtRollback() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord created =
                env.sessionRepository.bindNewSession("MEMORY:settings-time-rollback:user");
        SessionRecord stale = env.sessionRepository.findById(created.getSessionId());
        long staleUpdatedAt = stale.getUpdatedAt();

        env.sessionRepository.setModelOverride(created.getSessionId(), "openai:gpt-5.4");
        SessionRecord current = env.sessionRepository.findById(created.getSessionId());
        current.setUpdatedAt(staleUpdatedAt);
        env.sessionRepository.save(current);

        stale.setNdjson("runtime result");
        env.sessionRepository.save(stale);

        SessionRecord stored = env.sessionRepository.findById(created.getSessionId());
        assertThat(stored.getModelOverride()).isEqualTo("openai:gpt-5.4");
        assertThat(stored.getNdjson()).isEqualTo("runtime result");
    }

    /** 单个设置发生并发更新时，其他未冲突设置仍应接受全量保存的新值。 */
    @Test
    void shouldMergeConcurrentSessionSettingsPerField() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord current =
                env.sessionRepository.bindNewSession("MEMORY:per-field-settings:user");

        env.sessionRepository.setModelOverride(current.getSessionId(), "openai:gpt-5.4");
        current.setGoalStateJson("{\"goal\":\"local\"}");
        env.sessionRepository.save(current);

        SessionRecord stored = env.sessionRepository.findById(current.getSessionId());
        assertThat(stored.getModelOverride()).isEqualTo("openai:gpt-5.4");
        assertThat(stored.getGoalStateJson()).isEqualTo("{\"goal\":\"local\"}");
    }

    /** 新会话首次保存仍应接受完整的设置与状态初始值。 */
    @Test
    void shouldSaveInitialSessionSettings() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord current = new SessionRecord();
        current.setSessionId("initial-settings");
        current.setSourceKey("MEMORY:initial-settings:user");
        current.setBranchName("main");

        current.setModelOverride("openai:gpt-5.4");
        current.setServiceTierOverride("priority");
        current.setReasoningEffortOverride("high");
        current.setGoalStateJson("{\"goal\":\"active\"}");
        current.setLastLearningAt(42L);
        current.setUpdatedAt(System.currentTimeMillis());
        env.sessionRepository.save(current);

        SessionRecord stored = env.sessionRepository.findById(current.getSessionId());
        assertThat(stored.getModelOverride()).isEqualTo("openai:gpt-5.4");
        assertThat(stored.getServiceTierOverride()).isEqualTo("priority");
        assertThat(stored.getReasoningEffortOverride()).isEqualTo("high");
        assertThat(stored.getGoalStateJson()).isEqualTo("{\"goal\":\"active\"}");
        assertThat(stored.getLastLearningAt()).isEqualTo(42L);
    }

    /** 定向清空设置后，连续旧快照保存不得恢复已经删除的值。 */
    @Test
    void shouldPreserveConcurrentSessionSettingClears() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord initial = new SessionRecord();
        initial.setSessionId("cleared-settings");
        initial.setSourceKey("MEMORY:cleared-settings:user");
        initial.setBranchName("main");
        initial.setModelOverride("old-model");
        initial.setServiceTierOverride("priority");
        initial.setReasoningEffortOverride("high");
        initial.setGoalStateJson("{\"goal\":\"old\"}");
        initial.setLastLearningAt(42L);
        env.sessionRepository.save(initial);
        SessionRecord stale = env.sessionRepository.findById(initial.getSessionId());

        env.sessionRepository.setModelOverride(initial.getSessionId(), null);
        env.sessionRepository.setServiceTierOverride(initial.getSessionId(), null);
        env.sessionRepository.setReasoningEffortOverride(initial.getSessionId(), null);
        env.sessionRepository.setGoalState(initial.getSessionId(), null);
        env.sessionRepository.setLastLearningAt(initial.getSessionId(), 0L);
        env.sessionRepository.save(stale);
        env.sessionRepository.save(stale);

        SessionRecord stored = env.sessionRepository.findById(initial.getSessionId());
        assertThat(stored.getModelOverride()).isNull();
        assertThat(stored.getServiceTierOverride()).isNull();
        assertThat(stored.getReasoningEffortOverride()).isNull();
        assertThat(stored.getGoalStateJson()).isNull();
        assertThat(stored.getLastLearningAt()).isZero();
    }

    @Test
    void shouldApplyDurabilityAndSafetyPragmasOnEveryConnection() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        assertStoragePragmas(env.sqliteDatabase.openConnection());
        assertStoragePragmas(env.sqliteDatabase.openConnection());
    }

    @Test
    void shouldPersistSessionsWhenSearchIndexUnavailable() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        dropSessionSearchIndex(env.sqliteDatabase);

        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:no-fts-room:user");
        session.setNdjson("fallback searchable transcript");
        session.setTitle("fallback title");
        session.setCompressedSummary("fallback summary");
        env.sessionRepository.save(session);

        assertThat(env.sessionRepository.findById(session.getSessionId())).isNotNull();
        assertThat(env.sessionRepository.search("fallback", 10))
                .extracting(SessionRecord::getSessionId)
                .contains(session.getSessionId());
    }

    @Test
    void shouldDeleteSessionsWhenSearchIndexUnavailable() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:no-fts-delete:user");
        dropSessionSearchIndex(env.sqliteDatabase);

        env.sessionRepository.delete(session.getSessionId());

        assertThat(env.sessionRepository.findById(session.getSessionId())).isNull();
    }

    @Test
    void shouldKeepToolCallCountWhenRunIsSavedAfterToolCompletion() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:run-count:user");
        AgentRunRecord run = new AgentRunRecord();
        run.setRunId("run-tool-count-1");
        run.setSessionId(session.getSessionId());
        run.setSourceKey(session.getSourceKey());
        run.setStatus("running");
        run.setStartedAt(System.currentTimeMillis());
        run.setLastActivityAt(run.getStartedAt());
        env.agentRunRepository.saveRun(run);

        ToolCallRecord toolCall = new ToolCallRecord();
        toolCall.setToolCallId("tool-call-count-1");
        toolCall.setRunId(run.getRunId());
        toolCall.setSessionId(session.getSessionId());
        toolCall.setSourceKey(session.getSourceKey());
        toolCall.setToolName("todo");
        toolCall.setStatus("completed");
        toolCall.setStartedAt(run.getStartedAt());
        toolCall.setFinishedAt(System.currentTimeMillis());
        env.agentRunRepository.saveToolCall(toolCall);

        run.setStatus("success");
        run.setPhase("completed");
        run.setFinishedAt(System.currentTimeMillis());
        env.agentRunRepository.saveRun(run);

        assertThat(env.agentRunRepository.findRun(run.getRunId()).getToolCallCount()).isEqualTo(1);
    }

    @Test
    void shouldExcludeDeniedToolCallsFromRunToolCallCount() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session =
                env.sessionRepository.bindNewSession("MEMORY:run-denied-count:user");
        AgentRunRecord run = new AgentRunRecord();
        run.setRunId("run-tool-denied-count-1");
        run.setSessionId(session.getSessionId());
        run.setSourceKey(session.getSourceKey());
        run.setStatus("running");
        run.setStartedAt(System.currentTimeMillis());
        run.setLastActivityAt(run.getStartedAt());
        env.agentRunRepository.saveRun(run);

        ToolCallRecord completed =
                toolCall(run, session, "tool-call-denied-count-1", "todo", "completed");
        env.agentRunRepository.saveToolCall(completed);

        ToolCallRecord denied =
                toolCall(run, session, "tool-call-denied-count-2", "todo", "denied");
        env.agentRunRepository.saveToolCall(denied);

        run.setStatus("success");
        run.setPhase("completed");
        run.setFinishedAt(System.currentTimeMillis());
        env.agentRunRepository.saveRun(run);

        assertThat(env.agentRunRepository.findRun(run.getRunId()).getToolCallCount()).isEqualTo(1);
    }

    /** 工具调用计数只随终态边界变化，并在重复保存、跨运行迁移和运行重存后保持正确。 */
    @Test
    void shouldUpdateToolCallCountOnlyWhenTerminalStateChanges() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session =
                env.sessionRepository.bindNewSession("MEMORY:run-terminal-count:user");
        AgentRunRecord run = new AgentRunRecord();
        run.setRunId("run-tool-terminal-count-1");
        run.setSessionId(session.getSessionId());
        run.setSourceKey(session.getSourceKey());
        run.setStatus("running");
        run.setStartedAt(System.currentTimeMillis());
        run.setLastActivityAt(run.getStartedAt());
        env.agentRunRepository.saveRun(run);

        ToolCallRecord toolCall =
                toolCall(run, session, "tool-call-terminal-count-1", "todo", "completed");
        env.agentRunRepository.saveToolCall(toolCall);
        env.agentRunRepository.saveToolCall(toolCall);
        assertToolCallCount(env, run.getRunId(), 1);

        toolCall.setStatus("running");
        env.agentRunRepository.saveToolCall(toolCall);
        assertToolCallCount(env, run.getRunId(), 0);

        toolCall.setStatus("failed");
        env.agentRunRepository.saveToolCall(toolCall);
        toolCall.setStatus("completed");
        env.agentRunRepository.saveToolCall(toolCall);
        assertToolCallCount(env, run.getRunId(), 1);

        AgentRunRecord targetRun = new AgentRunRecord();
        targetRun.setRunId("run-tool-terminal-count-2");
        targetRun.setSessionId(session.getSessionId());
        targetRun.setSourceKey(session.getSourceKey());
        targetRun.setStatus("running");
        targetRun.setStartedAt(System.currentTimeMillis());
        targetRun.setLastActivityAt(targetRun.getStartedAt());
        env.agentRunRepository.saveRun(targetRun);

        toolCall.setRunId(targetRun.getRunId());
        env.agentRunRepository.saveToolCall(toolCall);
        assertToolCallCount(env, run.getRunId(), 0);
        assertToolCallCount(env, targetRun.getRunId(), 1);

        toolCall.setStatus("denied");
        env.agentRunRepository.saveToolCall(toolCall);
        targetRun.setStatus("success");
        env.agentRunRepository.saveRun(targetRun);
        assertToolCallCount(env, targetRun.getRunId(), 0);
    }

    /** 用量与陈旧运行扫描只应返回调用方需要的字段，不加载大预览和错误正文。 */
    @Test
    void shouldProjectLightweightUsageAndStaleRunColumns() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        long now = System.currentTimeMillis();
        AgentRunRecord usage = new AgentRunRecord();
        usage.setRunId("usage-projection-run");
        usage.setSessionId("usage-projection-session");
        usage.setSourceKey("MEMORY:usage-projection:user");
        usage.setStatus("success");
        usage.setProvider("provider-a");
        usage.setModel("model-a");
        usage.setInputTokens(10L);
        usage.setOutputTokens(20L);
        usage.setTotalTokens(30L);
        usage.setStartedAt(now - 2_000L);
        usage.setFinishedAt(now - 1_000L);
        usage.setInputPreview("large input preview");
        usage.setFinalReplyPreview("large reply preview");
        usage.setRecoveryHint("large recovery hint");
        usage.setError("large error");
        env.agentRunRepository.saveRun(usage);

        AgentRunRecord usageResult =
                env.agentRunRepository.listFinishedWithUsage(10).stream()
                        .filter(record -> usage.getRunId().equals(record.getRunId()))
                        .findFirst()
                        .orElse(null);
        assertThat(usageResult).isNotNull();
        assertThat(usageResult.getProvider()).isEqualTo("provider-a");
        assertThat(usageResult.getTotalTokens()).isEqualTo(30L);
        assertThat(usageResult.getInputPreview()).isNull();
        assertThat(usageResult.getFinalReplyPreview()).isNull();
        assertThat(usageResult.getRecoveryHint()).isNull();
        assertThat(usageResult.getError()).isNull();

        AgentRunRecord stale = new AgentRunRecord();
        stale.setRunId("stale-projection-run");
        stale.setSessionId("stale-projection-session");
        stale.setSourceKey("MEMORY:stale-projection:user");
        stale.setRunKind("conversation");
        stale.setStatus("running");
        stale.setStartedAt(now - 120_000L);
        stale.setLastActivityAt(now - 120_000L);
        stale.setInputPreview("large stale input");
        stale.setFinalReplyPreview("large stale reply");
        stale.setRecoveryHint("large stale recovery hint");
        stale.setError("large stale error");
        env.agentRunRepository.saveRun(stale);

        AgentRunRecord staleResult =
                env.agentRunRepository.listActiveBefore(now - 60_000L, 10).stream()
                        .filter(record -> stale.getRunId().equals(record.getRunId()))
                        .findFirst()
                        .orElse(null);
        assertThat(staleResult).isNotNull();
        assertThat(staleResult.getRunKind()).isEqualTo("conversation");
        assertThat(staleResult.getStatus()).isEqualTo("running");
        assertThat(staleResult.getInputPreview()).isNull();
        assertThat(staleResult.getFinalReplyPreview()).isNull();
        assertThat(staleResult.getRecoveryHint()).isNull();
        assertThat(staleResult.getError()).isNull();
    }

    /** 用量运行游标必须在同毫秒记录之间保持稳定倒序，且跨页不重不漏。 */
    @Test
    void shouldPageFinishedUsageRunsWithStableTieBreaker() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        env.agentRunRepository.saveRun(usageRun("usage-cursor-a", "success", 5000L, 10L));
        env.agentRunRepository.saveRun(usageRun("usage-cursor-c", "success", 5000L, 10L));
        env.agentRunRepository.saveRun(usageRun("usage-cursor-b", "success", 5000L, 10L));
        env.agentRunRepository.saveRun(usageRun("usage-cursor-old", "success", 4000L, 10L));
        env.agentRunRepository.saveRun(usageRun("usage-cursor-failed", "failed", 6000L, 10L));
        env.agentRunRepository.saveRun(usageRun("usage-cursor-zero", "success", 6000L, 0L));

        List<AgentRunRecord> first = env.agentRunRepository.listFinishedWithUsage(-1L, null, 2);
        assertThat(first)
                .extracting(AgentRunRecord::getRunId)
                .containsExactly("usage-cursor-c", "usage-cursor-b");

        AgentRunRecord firstCursor = first.get(first.size() - 1);
        List<AgentRunRecord> second =
                env.agentRunRepository.listFinishedWithUsage(
                        firstCursor.getFinishedAt(), firstCursor.getRunId(), 2);
        assertThat(second)
                .extracting(AgentRunRecord::getRunId)
                .containsExactly("usage-cursor-a", "usage-cursor-old");

        AgentRunRecord secondCursor = second.get(second.size() - 1);
        assertThat(
                        env.agentRunRepository.listFinishedWithUsage(
                                secondCursor.getFinishedAt(), secondCursor.getRunId(), 2))
                .isEmpty();
    }

    /** 陈旧运行应按调用方批次原子更新，并为每个运行只写一条恢复记录。 */
    @Test
    void shouldAtomicallyMarkExactStaleRunBatch() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        long staleAt = System.currentTimeMillis() - 120_000L;
        for (int index = 0; index < 3; index++) {
            AgentRunRecord run = new AgentRunRecord();
            run.setRunId("stale-atomic-batch-" + index);
            run.setSessionId("stale-atomic-session-" + index);
            run.setSourceKey("MEMORY:stale-atomic:" + index);
            run.setRunKind("scheduled");
            run.setStatus("running");
            run.setStartedAt(staleAt + index);
            run.setLastActivityAt(staleAt + index);
            env.agentRunRepository.saveRun(run);
        }

        long before = System.currentTimeMillis() - 60_000L;
        List<AgentRunRecord> first =
                env.agentRunRepository.markStaleRuns(before, System.currentTimeMillis(), 2);
        List<AgentRunRecord> second =
                env.agentRunRepository.markStaleRuns(before, System.currentTimeMillis(), 2);
        List<AgentRunRecord> third =
                env.agentRunRepository.markStaleRuns(before, System.currentTimeMillis(), 2);

        assertThat(first).hasSize(2);
        assertThat(second).hasSize(1);
        assertThat(third).isEmpty();
        for (AgentRunRecord stale : first) {
            assertThat(env.agentRunRepository.findRun(stale.getRunId()).getStatus())
                    .isEqualTo("recoverable");
            assertThat(env.agentRunRepository.listRecoveries(stale.getRunId())).hasSize(1);
        }
        AgentRunRecord last = second.get(0);
        assertThat(env.agentRunRepository.findRun(last.getRunId()).getStatus())
                .isEqualTo("recoverable");
        assertThat(env.agentRunRepository.listRecoveries(last.getRunId())).hasSize(1);
    }

    /** 恢复记录写入失败时，陈旧运行的状态更新必须随同事务回滚。 */
    @Test
    void shouldRollbackStaleRunWhenRecoveryInsertFails() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        long staleAt = System.currentTimeMillis() - 120_000L;
        AgentRunRecord run = new AgentRunRecord();
        run.setRunId("stale-atomic-rollback");
        run.setSessionId("stale-atomic-rollback-session");
        run.setSourceKey("MEMORY:stale-atomic-rollback:user");
        run.setRunKind("scheduled");
        run.setStatus("running");
        run.setStartedAt(staleAt);
        run.setLastActivityAt(staleAt);
        env.agentRunRepository.saveRun(run);

        Connection connection = env.sqliteDatabase.openConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute(
                        "create trigger fail_stale_recovery before insert on run_recoveries when new.recovery_type = 'stale_heartbeat' begin select raise(abort, 'forced stale recovery failure'); end");
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }

        Exception failure = null;
        try {
            env.agentRunRepository.markStaleRuns(
                    System.currentTimeMillis() - 60_000L, System.currentTimeMillis(), 10);
        } catch (Exception e) {
            failure = e;
        }

        assertThat(failure).isNotNull();
        assertThat(env.agentRunRepository.findRun(run.getRunId()).getStatus()).isEqualTo("running");
        assertThat(env.agentRunRepository.listRecoveries(run.getRunId())).isEmpty();
    }

    @Test
    void shouldClearSessionScopedSecurityStateWhenBranching() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord session = env.sessionRepository.bindNewSession("MEMORY:secure-branch:user");
        SqliteAgentSession agentSession = new SqliteAgentSession(session, env.sessionRepository);
        agentSession.getContext().put("ordinary_context", "keep-me");
        env.dangerousCommandApprovalService.enableSessionAutoApproval(agentSession);
        env.dangerousCommandApprovalService.storePendingApproval(
                agentSession,
                "execute_shell",
                "recursive_delete",
                "recursive delete",
                "rm -rf workspace/cache");
        env.dangerousCommandApprovalService.approve(
                agentSession, DangerousCommandApprovalService.ApprovalScope.SESSION, "tester");
        agentSession.pending(true, "restart_interrupted");
        agentSession.updateSnapshot();
        session.setGoalStateJson("{\"status\":\"active\",\"goal\":\"parent goal\"}");
        env.sessionRepository.save(session);

        SessionRecord clone =
                env.sessionRepository.cloneSession(
                        "MEMORY:secure-branch:user", session.getSessionId(), "safe-review");
        SqliteAgentSession clonedSession = new SqliteAgentSession(clone, env.sessionRepository);

        assertThat(clonedSession.getContext().get("ordinary_context")).isEqualTo("keep-me");
        assertThat(env.dangerousCommandApprovalService.isSessionAutoApprovalEnabled(clonedSession))
                .isFalse();
        assertThat(env.dangerousCommandApprovalService.getPendingApproval(clonedSession)).isNull();
        assertThat(
                        env.dangerousCommandApprovalService.isSessionApproved(
                                clonedSession, "recursive_delete"))
                .isFalse();
        assertThat(clonedSession.isPending()).isFalse();
        assertThat(clonedSession.getPendingReason()).isNull();
        assertThat(clonedSession.getPendingMarkedAt()).isZero();
        assertThat(clonedSession.getPendingClearedAt()).isZero();
        assertThat(clonedSession.getPendingLastReason()).isNull();
        assertThat(clone.getGoalStateJson()).isNull();
        assertThat(env.sessionRepository.findById(session.getSessionId()).getGoalStateJson())
                .contains("\"status\":\"active\"");
    }

    @Test
    void shouldFindResumeCandidatesByUniqueIdPrefixOrExactTitle() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord alpha = env.sessionRepository.bindNewSession("MEMORY:resume-a:user");
        alpha.setTitle("客户周报");
        env.sessionRepository.save(alpha);
        SessionRecord beta = env.sessionRepository.bindNewSession("MEMORY:resume-b:user");
        beta.setTitle("客户日报");
        env.sessionRepository.save(beta);

        List<SessionRecord> byPrefix =
                env.sessionRepository.findResumeCandidates(alpha.getSessionId().substring(0, 8), 3);
        List<SessionRecord> byTitle = env.sessionRepository.findResumeCandidates("客户日报", 3);
        List<SessionRecord> partialTitle = env.sessionRepository.findResumeCandidates("客户", 3);

        assertThat(byPrefix).hasSize(1);
        assertThat(byPrefix.get(0).getSessionId()).isEqualTo(alpha.getSessionId());
        assertThat(byTitle).hasSize(1);
        assertThat(byTitle.get(0).getSessionId()).isEqualTo(beta.getSessionId());
        assertThat(partialTitle).isEmpty();
    }

    @Test
    void shouldReturnMultipleResumeCandidatesForAmbiguousExactTitle() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord first = env.sessionRepository.bindNewSession("MEMORY:resume-shared-a:user");
        first.setTitle("共享标题");
        env.sessionRepository.save(first);
        SessionRecord second = env.sessionRepository.bindNewSession("MEMORY:resume-shared-b:user");
        second.setTitle("共享标题");
        env.sessionRepository.save(second);

        List<SessionRecord> candidates = env.sessionRepository.findResumeCandidates("共享标题", 3);

        assertThat(candidates).hasSize(2);
        assertThat(candidates)
                .extracting(SessionRecord::getSessionId)
                .contains(first.getSessionId(), second.getSessionId());
    }

    @Test
    void shouldListSessionLineageFromRootAndResolveLatestDescendantPath() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        SessionRecord root =
                env.sessionRepository.bindNewSession("MEMORY:lineage-storage-root:user");
        root.setTitle("root");
        root.setCreatedAt(100L);
        root.setUpdatedAt(100L);
        env.sessionRepository.save(root);

        SessionRecord oldChild =
                env.sessionRepository.cloneSession(
                        "MEMORY:lineage-storage-old:user", root.getSessionId(), "old");
        oldChild.setTitle("old");
        oldChild.setCreatedAt(200L);
        oldChild.setUpdatedAt(200L);
        env.sessionRepository.save(oldChild);

        SessionRecord newChild =
                env.sessionRepository.cloneSession(
                        "MEMORY:lineage-storage-new:user", root.getSessionId(), "new");
        newChild.setTitle("new");
        newChild.setCreatedAt(300L);
        newChild.setUpdatedAt(300L);
        env.sessionRepository.save(newChild);

        SessionRecord grandchild =
                env.sessionRepository.cloneSession(
                        "MEMORY:lineage-storage-grand:user", newChild.getSessionId(), "grand");
        grandchild.setTitle("grand");
        grandchild.setCreatedAt(400L);
        grandchild.setUpdatedAt(400L);
        env.sessionRepository.save(grandchild);

        List<SessionRecord> lineage = env.sessionRepository.listLineage(newChild.getSessionId());
        List<String> latestPath = env.sessionRepository.latestDescendantPath(root.getSessionId());

        assertThat(lineage)
                .extracting(SessionRecord::getSessionId)
                .contains(
                        root.getSessionId(),
                        oldChild.getSessionId(),
                        newChild.getSessionId(),
                        grandchild.getSessionId());
        assertThat(env.sessionRepository.resolveRootSessionId(grandchild.getSessionId()))
                .isEqualTo(root.getSessionId());
        assertThat(latestPath)
                .containsExactly(
                        root.getSessionId(), newChild.getSessionId(), grandchild.getSessionId());
    }

    private void assertStoragePragmas(Connection connection) throws Exception {
        try {
            assertThat(pragmaInt(connection, "secure_delete")).isEqualTo(1);
            assertThat(pragmaInt(connection, "cell_size_check")).isEqualTo(1);
            assertThat(pragmaInt(connection, "synchronous")).isEqualTo(2);
        } finally {
            connection.close();
        }
    }

    private int pragmaInt(Connection connection, String name) throws Exception {
        Statement statement = connection.createStatement();
        try {
            ResultSet resultSet = statement.executeQuery("pragma " + name);
            try {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            } finally {
                resultSet.close();
            }
        } finally {
            statement.close();
        }
    }

    /**
     * 构造工具调用记录，复用当前运行和会话的审计归属字段。
     *
     * @param run 当前运行记录。
     * @param session 当前会话记录。
     * @param toolCallId 工具调用标识。
     * @param toolName 工具名称。
     * @param status 工具调用状态。
     * @return 返回可落库的工具调用记录。
     */
    private ToolCallRecord toolCall(
            AgentRunRecord run,
            SessionRecord session,
            String toolCallId,
            String toolName,
            String status) {
        ToolCallRecord toolCall = new ToolCallRecord();
        toolCall.setToolCallId(toolCallId);
        toolCall.setRunId(run.getRunId());
        toolCall.setSessionId(session.getSessionId());
        toolCall.setSourceKey(session.getSourceKey());
        toolCall.setToolName(toolName);
        toolCall.setStatus(status);
        toolCall.setStartedAt(run.getStartedAt());
        toolCall.setFinishedAt(System.currentTimeMillis());
        return toolCall;
    }

    /**
     * 构造用于稳定游标测试的轻量用量运行。
     *
     * @param runId 运行标识。
     * @param status 运行状态。
     * @param finishedAt 完成时间。
     * @param totalTokens 总用量。
     * @return 返回可落库的运行记录。
     */
    private AgentRunRecord usageRun(
            String runId, String status, long finishedAt, long totalTokens) {
        AgentRunRecord run = new AgentRunRecord();
        run.setRunId(runId);
        run.setSessionId("usage-cursor-session");
        run.setSourceKey("MEMORY:usage-cursor:user");
        run.setStatus(status);
        run.setProvider("provider-a");
        run.setModel("model-a");
        run.setInputTokens(totalTokens);
        run.setTotalTokens(totalTokens);
        run.setStartedAt(finishedAt - 100L);
        run.setFinishedAt(finishedAt);
        return run;
    }

    /**
     * 断言运行记录中的工具调用计数。
     *
     * @param env 测试环境。
     * @param runId 运行标识。
     * @param expected 预期计数。
     */
    private void assertToolCallCount(TestEnvironment env, String runId, int expected)
            throws Exception {
        AgentRunRecord stored = env.agentRunRepository.findRun(runId);
        assertThat(stored).isNotNull();
        assertThat(stored.getToolCallCount()).isEqualTo(expected);
    }

    private void dropSessionSearchIndex(SqliteDatabase database) throws Exception {
        Connection connection = database.openConnection();
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute("drop table if exists sessions_fts");
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }
}
