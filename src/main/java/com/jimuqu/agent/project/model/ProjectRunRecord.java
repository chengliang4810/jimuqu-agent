package com.jimuqu.agent.project.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectRunRecord {
    private String runId;
    private String projectId;
    private String todoId;
    private String agentName;
    private String sessionId;
    private String workDir;
    private String model;
    private String allowedToolsJson;
    private String loadedMemoryFilesJson;
    private String status;
    private String summary;
    private long startedAt;
    private long finishedAt;
}
