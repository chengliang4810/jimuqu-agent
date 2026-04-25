package com.jimuqu.agent.project.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectEventRecord {
    private String eventId;
    private String projectId;
    private String todoId;
    private String eventType;
    private String actor;
    private String message;
    private String metadataJson;
    private long createdAt;
}
