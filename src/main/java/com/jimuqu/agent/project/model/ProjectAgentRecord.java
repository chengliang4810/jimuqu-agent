package com.jimuqu.agent.project.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectAgentRecord {
    private String projectId;
    private String agentName;
    private String roleHint;
    private long createdAt;
    private long updatedAt;
}
