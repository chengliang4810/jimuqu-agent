package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.storage.repository.ReadOnlyProfileSessionRepository;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.storage.repository.SqliteSessionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 验证读写会话仓储共享完整列定义并产生一致映射。 */
public class SessionColumnDefinitionTest {

    /** 写仓储与跨 Profile 只读仓储必须读取同一份完整会话记录。 */
    @Test
    void shouldMapEveryPersistedSessionFieldConsistently() throws Exception {
        Path stateDb = Files.createTempDirectory("session-columns").resolve("state.db");
        AppConfig config = new AppConfig();
        config.getRuntime().setStateDb(stateDb.toString());
        SqliteDatabase database = new SqliteDatabase(config);
        SessionRecord writable;
        try {
            SqliteSessionRepository repository = new SqliteSessionRepository(database);
            repository.save(completeRecord());
            writable = repository.findById("session-columns");
        } finally {
            database.shutdown();
        }

        ReadOnlyProfileSessionRepository repository = new ReadOnlyProfileSessionRepository(stateDb);
        SessionRecord readOnly = repository.findById("session-columns");

        assertThat(readOnly).usingRecursiveComparison().isEqualTo(writable);
    }

    /** 构造每个持久化字段都带唯一值的会话记录，缺列时查询会立即失败。 */
    private SessionRecord completeRecord() {
        SessionRecord record = new SessionRecord();
        record.setSessionId("session-columns");
        record.setSourceKey("MEMORY:session-columns:user");
        record.setBranchName("review");
        record.setParentSessionId("parent-columns");
        record.setModelOverride("model-columns");
        record.setServiceTierOverride("priority");
        record.setReasoningEffortOverride("high");
        record.setPlatformMessageId("platform-columns");
        record.setMetadataJson("{\"metadata\":\"columns\"}");
        record.setNdjson("{\"role\":\"user\",\"content\":\"columns\"}");
        record.setTitle("title-columns");
        record.setCompressedSummary("summary-columns");
        record.setSystemPromptSnapshot("system-columns");
        record.setAgentSnapshotJson("{\"agent\":\"columns\"}");
        record.setGoalStateJson("{\"goal\":\"columns\"}");
        record.setLastLearningAt(101L);
        record.setLastCompressionAt(102L);
        record.setLastCompressionInputTokens(103);
        record.setCompressionFailureCount(104);
        record.setLastCompressionFailedAt(105L);
        record.setLastInputTokens(106L);
        record.setLastOutputTokens(107L);
        record.setLastReasoningTokens(108L);
        record.setLastCacheReadTokens(109L);
        record.setLastCacheWriteTokens(110L);
        record.setLastTotalTokens(111L);
        record.setCumulativeInputTokens(112L);
        record.setCumulativeOutputTokens(113L);
        record.setCumulativeReasoningTokens(114L);
        record.setCumulativeCacheReadTokens(115L);
        record.setCumulativeCacheWriteTokens(116L);
        record.setCumulativeTotalTokens(117L);
        record.setLastUsageAt(118L);
        record.setLastResolvedProvider("provider-columns");
        record.setLastResolvedModel("resolved-model-columns");
        record.setCreatedAt(119L);
        record.setUpdatedAt(120L);
        return record;
    }
}
