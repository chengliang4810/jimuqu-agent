package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.core.model.SessionRecord;
import java.sql.ResultSet;

/** 会话记录结果集映射工具，供多个会话仓储共享同一份列映射逻辑。 */
final class SessionRecordMapper {

    /** 会话结果集映射所需的完整稳定列名，所有会话仓储必须复用此定义。 */
    private static final String[] COLUMN_NAMES = {
        "session_id",
        "source_key",
        "branch_name",
        "parent_session_id",
        "model_override",
        "service_tier_override",
        "reasoning_effort_override",
        "platform_message_id",
        "metadata_json",
        "ndjson",
        "title",
        "compressed_summary",
        "system_prompt_snapshot",
        "agent_snapshot_json",
        "goal_state_json",
        "last_learning_at",
        "last_compression_at",
        "last_compression_input_tokens",
        "compression_failure_count",
        "last_compression_failed_at",
        "last_input_tokens",
        "last_output_tokens",
        "last_reasoning_tokens",
        "last_cache_read_tokens",
        "last_cache_write_tokens",
        "last_total_tokens",
        "cumulative_input_tokens",
        "cumulative_output_tokens",
        "cumulative_reasoning_tokens",
        "cumulative_cache_read_tokens",
        "cumulative_cache_write_tokens",
        "cumulative_total_tokens",
        "last_usage_at",
        "last_resolved_provider",
        "last_resolved_model",
        "created_at",
        "updated_at"
    };

    /** 无表别名的完整会话查询列。 */
    private static final String SELECT_COLUMNS = String.join(", ", COLUMN_NAMES);

    private SessionRecordMapper() {
        // 工具类，禁止实例化。
    }

    /**
     * 返回无表别名的完整会话查询列。
     *
     * @return 按映射顺序排列的查询列。
     */
    static String selectColumns() {
        return SELECT_COLUMNS;
    }

    /**
     * 返回带指定表别名的完整会话查询列。
     *
     * @param tableAlias SQL 表别名。
     * @return 按映射顺序排列并带表别名的查询列。
     */
    static String selectColumns(String tableAlias) {
        if (tableAlias == null || tableAlias.trim().length() == 0) {
            throw new IllegalArgumentException("会话查询表别名不能为空");
        }
        String prefix = tableAlias.trim() + ".";
        StringBuilder columns = new StringBuilder();
        for (String columnName : COLUMN_NAMES) {
            if (columns.length() > 0) {
                columns.append(", ");
            }
            columns.append(prefix).append(columnName);
        }
        return columns.toString();
    }

    /** 将结果集当前行映射为 SessionRecord。 */
    static SessionRecord map(ResultSet resultSet) throws Exception {
        SessionRecord record = new SessionRecord();
        record.setSessionId(resultSet.getString("session_id"));
        record.setSourceKey(resultSet.getString("source_key"));
        record.setBranchName(resultSet.getString("branch_name"));
        record.setParentSessionId(resultSet.getString("parent_session_id"));
        record.setModelOverride(resultSet.getString("model_override"));
        record.setServiceTierOverride(resultSet.getString("service_tier_override"));
        record.setReasoningEffortOverride(resultSet.getString("reasoning_effort_override"));
        record.setPlatformMessageId(resultSet.getString("platform_message_id"));
        record.setMetadataJson(resultSet.getString("metadata_json"));
        record.setNdjson(resultSet.getString("ndjson"));
        record.setPersistedNdjson(record.getNdjson());
        record.setTitle(resultSet.getString("title"));
        record.setCompressedSummary(resultSet.getString("compressed_summary"));
        record.setSystemPromptSnapshot(resultSet.getString("system_prompt_snapshot"));
        record.setAgentSnapshotJson(resultSet.getString("agent_snapshot_json"));
        record.setGoalStateJson(resultSet.getString("goal_state_json"));
        record.setLastLearningAt(resultSet.getLong("last_learning_at"));
        record.setLastCompressionAt(resultSet.getLong("last_compression_at"));
        record.setLastCompressionInputTokens(resultSet.getInt("last_compression_input_tokens"));
        record.setCompressionFailureCount(resultSet.getInt("compression_failure_count"));
        record.setLastCompressionFailedAt(resultSet.getLong("last_compression_failed_at"));
        record.setLastInputTokens(resultSet.getLong("last_input_tokens"));
        record.setLastOutputTokens(resultSet.getLong("last_output_tokens"));
        record.setLastReasoningTokens(resultSet.getLong("last_reasoning_tokens"));
        record.setLastCacheReadTokens(resultSet.getLong("last_cache_read_tokens"));
        record.setLastCacheWriteTokens(resultSet.getLong("last_cache_write_tokens"));
        record.setLastTotalTokens(resultSet.getLong("last_total_tokens"));
        record.setCumulativeInputTokens(resultSet.getLong("cumulative_input_tokens"));
        record.setCumulativeOutputTokens(resultSet.getLong("cumulative_output_tokens"));
        record.setCumulativeReasoningTokens(resultSet.getLong("cumulative_reasoning_tokens"));
        record.setCumulativeCacheReadTokens(resultSet.getLong("cumulative_cache_read_tokens"));
        record.setCumulativeCacheWriteTokens(resultSet.getLong("cumulative_cache_write_tokens"));
        record.setCumulativeTotalTokens(resultSet.getLong("cumulative_total_tokens"));
        record.setLastUsageAt(resultSet.getLong("last_usage_at"));
        record.setLastResolvedProvider(resultSet.getString("last_resolved_provider"));
        record.setLastResolvedModel(resultSet.getString("last_resolved_model"));
        record.setCreatedAt(resultSet.getLong("created_at"));
        record.setUpdatedAt(resultSet.getLong("updated_at"));
        record.setPersistedConcurrentSettings(concurrentSettings(record));
        return record;
    }

    /** 提取需要防止旧快照覆盖的会话设置。 */
    private static Object[] concurrentSettings(SessionRecord record) {
        return new Object[] {
            record.getModelOverride(),
            record.getServiceTierOverride(),
            record.getReasoningEffortOverride(),
            record.getGoalStateJson(),
            Long.valueOf(record.getLastLearningAt())
        };
    }
}
