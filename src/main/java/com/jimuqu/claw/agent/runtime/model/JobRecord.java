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
public class JobRecord {
    private String jobId;
    private String name;
    private String prompt;
    private String schedule;
    private String workspaceRoot;
    private String modelAlias;
    private String deliverTarget;
    private JobStatus status;
    @Builder.Default
    private List<String> skillNames = new ArrayList<String>();
    private String lastResultSummary;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastRunAt;
    private Instant nextRunAt;
}
