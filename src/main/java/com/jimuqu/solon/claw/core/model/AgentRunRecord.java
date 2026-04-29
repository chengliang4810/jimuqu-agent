package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Agent 单轮运行记录。 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRunRecord {
    private String runId;
    private String sessionId;
    private String sourceKey;
    private String agentName;
    private String agentSnapshotJson;
    private String status;
    private String inputPreview;
    private String finalReplyPreview;
    private String provider;
    private String model;
    private int attempts;
    private long inputTokens;
    private long outputTokens;
    private long totalTokens;
    private long startedAt;
    private long finishedAt;
    private String error;
}
