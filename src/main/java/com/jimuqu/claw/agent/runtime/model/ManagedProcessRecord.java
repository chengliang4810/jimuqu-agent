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
public class ManagedProcessRecord {
    private String processId;
    private Long pid;
    private String command;
    @Builder.Default
    private List<String> arguments = new ArrayList<String>();
    private String workingDirectory;
    private ProcessStatus status;
    private Integer exitCode;
    private String stdout;
    private String stderr;
    private Instant startedAt;
    private Instant completedAt;
}
