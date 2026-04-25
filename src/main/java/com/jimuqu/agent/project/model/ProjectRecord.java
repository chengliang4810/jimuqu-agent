package com.jimuqu.agent.project.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectRecord {
    private String projectId;
    private String slug;
    private String title;
    private String goal;
    private String status;
    private String currentTodoId;
    private long createdAt;
    private long updatedAt;
}
