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
     * 创建时间。
     */
    private long createdAt;

    /**
     * 更新时间。
     */
    private long updatedAt;
}
