package com.jimuqu.agent.project.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectTodoRecord {
    private String todoId;
    private String projectId;
    private String parentTodoId;
    private String todoNo;
    private String title;
    private String description;
    private String status;
    private String assignedAgent;
    private String priority;
    private int sortOrder;
    private long createdAt;
    private long updatedAt;
    private long finishedAt;
    private int childTotal;
    private int childDone;
}
