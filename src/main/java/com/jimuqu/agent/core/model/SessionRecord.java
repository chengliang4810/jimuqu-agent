package com.jimuqu.agent.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 会话持久化记录。
 */
@Getter
@Setter
@NoArgsConstructor
public class SessionRecord {
    /**
     * 会话 ID。
     */
    private String sessionId;

    /**
     * 来源键。
     */
    private String sourceKey;

    /**
     * 分支名。
     */
    private String branchName;

    /**
     * 父会话 ID。
     */
    private String parentSessionId;

    /**
     * 模型覆盖配置。
     */
    private String modelOverride;

    /**
     * 会话消息 NDJSON。
     */
    private String ndjson;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 最近一次压缩生成的结构化摘要。
     */
    private String compressedSummary;

    /**
     * 会话冻结后的系统提示词快照。
     */
    private String systemPromptSnapshot;

    /**
     * ReAct/AgentSession 的 FlowContext 快照 JSON。
     */
    private String agentSnapshotJson;

    /**
     * 最近一次学习闭环执行时间。
     */
    private long lastLearningAt;

    /**
     * 最近一次压缩时间。
     */
    private long lastCompressionAt;

    /**
     * 最近一次压缩前估算的输入 token 数。
     */
    private int lastCompressionInputTokens;

    /**
     * 压缩连续失败次数。
     */
    private int compressionFailureCount;

    /**
     * 最近一次压缩失败时间。
     */
    private long lastCompressionFailedAt;

    /**
     * 创建时间。
     */
    private long createdAt;

    /**
     * 更新时间。
     */
    private long updatedAt;
}
