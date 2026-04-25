package com.jimuqu.agent.project.repository;

import com.jimuqu.agent.project.model.ProjectAgentRecord;
import com.jimuqu.agent.project.model.ProjectEventRecord;
import com.jimuqu.agent.project.model.ProjectQuestionRecord;
import com.jimuqu.agent.project.model.ProjectRecord;
import com.jimuqu.agent.project.model.ProjectRunRecord;
import com.jimuqu.agent.project.model.ProjectTodoRecord;

import java.util.List;

public interface ProjectRepository {
    ProjectRecord saveProject(ProjectRecord project) throws Exception;

    ProjectRecord findProjectById(String projectId) throws Exception;

    ProjectRecord findProjectBySlug(String slug) throws Exception;

    List<ProjectRecord> listProjects() throws Exception;

    ProjectAgentRecord saveAgent(ProjectAgentRecord agent) throws Exception;

    List<ProjectAgentRecord> listAgents(String projectId) throws Exception;

    ProjectTodoRecord saveTodo(ProjectTodoRecord todo) throws Exception;

    ProjectTodoRecord findTodoById(String todoId) throws Exception;

    ProjectTodoRecord findTodoByNo(String projectId, String todoNo) throws Exception;

    List<ProjectTodoRecord> listTodos(String projectId) throws Exception;

    List<ProjectTodoRecord> listChildTodos(String parentTodoId) throws Exception;

    int nextSortOrder(String projectId) throws Exception;

    ProjectRunRecord saveRun(ProjectRunRecord run) throws Exception;

    List<ProjectRunRecord> listRuns(String projectId, int limit) throws Exception;

    ProjectQuestionRecord saveQuestion(ProjectQuestionRecord question) throws Exception;

    ProjectQuestionRecord findQuestionById(String questionId) throws Exception;

    List<ProjectQuestionRecord> listQuestions(String projectId, String status) throws Exception;

    ProjectEventRecord saveEvent(ProjectEventRecord event) throws Exception;

    List<ProjectEventRecord> listEvents(String projectId, int limit) throws Exception;
}
