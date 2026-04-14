package com.jimuqu.claw.agent.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallRecord {
    private String runId;
    private String toolName;
    private String argumentsJson;
    private String resultPreview;
    private Boolean success;
    private Instant startedAt;
    private Instant completedAt;
}
