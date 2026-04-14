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
public class ChildRunRecord {
    private String runId;
    private String parentRunId;
    private RunStatus status;
    private String summary;
    private Instant startedAt;
    private Instant completedAt;
}
