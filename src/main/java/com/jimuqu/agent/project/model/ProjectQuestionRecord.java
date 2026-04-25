package com.jimuqu.agent.project.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProjectQuestionRecord {
    private String questionId;
    private String projectId;
    private String todoId;
    private String askedBy;
    private String question;
    private String answer;
    private String status;
    private long createdAt;
    private long answeredAt;
}
