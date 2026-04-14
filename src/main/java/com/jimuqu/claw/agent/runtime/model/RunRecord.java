package com.jimuqu.claw.agent.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunRecord {
    private String runId;
    private String parentRunId;
    private String sessionId;
    private RunStatus status;
    private String source;
    private String modelAlias;
    private String userMessage;
    private String responseText;
    private String errorMessage;
    private String requestDigest;
    @Builder.Default
    private List<ChildRunRecord> children = new ArrayList<ChildRunRecord>();
    @Builder.Default
    private List<ToolCallRecord> toolCalls = new ArrayList<ToolCallRecord>();
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
}
