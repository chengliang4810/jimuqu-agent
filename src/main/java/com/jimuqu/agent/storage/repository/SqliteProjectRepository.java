package com.jimuqu.agent.storage.repository;

import com.jimuqu.agent.project.model.ProjectAgentRecord;
import com.jimuqu.agent.project.model.ProjectEventRecord;
import com.jimuqu.agent.project.model.ProjectQuestionRecord;
import com.jimuqu.agent.project.model.ProjectRecord;
import com.jimuqu.agent.project.model.ProjectRunRecord;
import com.jimuqu.agent.project.model.ProjectTodoRecord;
import com.jimuqu.agent.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class SqliteProjectRepository implements ProjectRepository {
    private final SqliteDatabase database;

    @Override
    public ProjectRecord saveProject(ProjectRecord project) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into projects (project_id, slug, title, goal, status, current_todo_id, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, project.getProjectId());
            statement.setString(2, project.getSlug());
            statement.setString(3, project.getTitle());
            statement.setString(4, project.getGoal());
            statement.setString(5, project.getStatus());
            statement.setString(6, project.getCurrentTodoId());
            statement.setLong(7, project.getCreatedAt());
            statement.setLong(8, project.getUpdatedAt());
            statement.executeUpdate();
            statement.close();
            return project;
        } finally {
            connection.close();
        }
    }

    @Override
    public ProjectRecord findProjectById(String projectId) throws Exception {
        return findProject("project_id", projectId);
    }

    @Override
    public ProjectRecord findProjectBySlug(String slug) throws Exception {
        return findProject("slug", slug);
    }

    @Override
    public List<ProjectRecord> listProjects() throws Exception {
        List<ProjectRecord> projects = new ArrayList<ProjectRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from projects order by updated_at desc");
            ResultSet rs = statement.executeQuery();
            try {
                while (rs.next()) {
                    projects.add(mapProject(rs));
                }
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return projects;
    }

    @Override
    public ProjectAgentRecord saveAgent(ProjectAgentRecord agent) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into project_agents (project_id, agent_name, role_hint, created_at, updated_at) values (?, ?, ?, ?, ?)");
            statement.setString(1, agent.getProjectId());
            statement.setString(2, agent.getAgentName());
            statement.setString(3, agent.getRoleHint());
            statement.setLong(4, agent.getCreatedAt());
            statement.setLong(5, agent.getUpdatedAt());
            statement.executeUpdate();
            statement.close();
            return agent;
        } finally {
            connection.close();
        }
    }

    @Override
    public List<ProjectAgentRecord> listAgents(String projectId) throws Exception {
        List<ProjectAgentRecord> agents = new ArrayList<ProjectAgentRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_agents where project_id = ? order by agent_name asc");
            statement.setString(1, projectId);
            ResultSet rs = statement.executeQuery();
            try {
                while (rs.next()) {
                    ProjectAgentRecord agent = new ProjectAgentRecord();
                    agent.setProjectId(rs.getString("project_id"));
                    agent.setAgentName(rs.getString("agent_name"));
                    agent.setRoleHint(rs.getString("role_hint"));
                    agent.setCreatedAt(rs.getLong("created_at"));
                    agent.setUpdatedAt(rs.getLong("updated_at"));
                    agents.add(agent);
                }
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return agents;
    }

    @Override
    public ProjectTodoRecord saveTodo(ProjectTodoRecord todo) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into project_todos (todo_id, project_id, parent_todo_id, todo_no, title, description, status, assigned_agent, priority, sort_order, created_at, updated_at, finished_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, todo.getTodoId());
            statement.setString(2, todo.getProjectId());
            statement.setString(3, todo.getParentTodoId());
            statement.setString(4, todo.getTodoNo());
            statement.setString(5, todo.getTitle());
            statement.setString(6, todo.getDescription());
            statement.setString(7, todo.getStatus());
            statement.setString(8, todo.getAssignedAgent());
            statement.setString(9, todo.getPriority());
            statement.setInt(10, todo.getSortOrder());
            statement.setLong(11, todo.getCreatedAt());
            statement.setLong(12, todo.getUpdatedAt());
            statement.setLong(13, todo.getFinishedAt());
            statement.executeUpdate();
            statement.close();
            return todo;
        } finally {
            connection.close();
        }
    }

    @Override
    public ProjectTodoRecord findTodoById(String todoId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_todos where todo_id = ?");
            statement.setString(1, todoId);
            ResultSet rs = statement.executeQuery();
            try {
                return rs.next() ? mapTodo(rs) : null;
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public ProjectTodoRecord findTodoByNo(String projectId, String todoNo) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_todos where project_id = ? and todo_no = ?");
            statement.setString(1, projectId);
            statement.setString(2, todoNo);
            ResultSet rs = statement.executeQuery();
            try {
                return rs.next() ? mapTodo(rs) : null;
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public List<ProjectTodoRecord> listTodos(String projectId) throws Exception {
        List<ProjectTodoRecord> todos = new ArrayList<ProjectTodoRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_todos where project_id = ? order by sort_order asc, created_at asc");
            statement.setString(1, projectId);
            ResultSet rs = statement.executeQuery();
            try {
                while (rs.next()) {
                    todos.add(mapTodo(rs));
                }
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        fillChildCounts(todos);
        return todos;
    }

    @Override
    public List<ProjectTodoRecord> listChildTodos(String parentTodoId) throws Exception {
        List<ProjectTodoRecord> todos = new ArrayList<ProjectTodoRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_todos where parent_todo_id = ? order by sort_order asc, created_at asc");
            statement.setString(1, parentTodoId);
            ResultSet rs = statement.executeQuery();
            try {
                while (rs.next()) {
                    todos.add(mapTodo(rs));
                }
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return todos;
    }

    @Override
    public int nextSortOrder(String projectId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select coalesce(max(sort_order), 0) + 1 from project_todos where project_id = ?");
            statement.setString(1, projectId);
            ResultSet rs = statement.executeQuery();
            try {
                return rs.next() ? rs.getInt(1) : 1;
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public ProjectRunRecord saveRun(ProjectRunRecord run) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into project_runs (run_id, project_id, todo_id, agent_name, session_id, work_dir, model, allowed_tools_json, loaded_memory_files_json, status, summary, started_at, finished_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, run.getRunId());
            statement.setString(2, run.getProjectId());
            statement.setString(3, run.getTodoId());
            statement.setString(4, run.getAgentName());
            statement.setString(5, run.getSessionId());
            statement.setString(6, run.getWorkDir());
            statement.setString(7, run.getModel());
            statement.setString(8, run.getAllowedToolsJson());
            statement.setString(9, run.getLoadedMemoryFilesJson());
            statement.setString(10, run.getStatus());
            statement.setString(11, run.getSummary());
            statement.setLong(12, run.getStartedAt());
            statement.setLong(13, run.getFinishedAt());
            statement.executeUpdate();
            statement.close();
            return run;
        } finally {
            connection.close();
        }
    }

    @Override
    public List<ProjectRunRecord> listRuns(String projectId, int limit) throws Exception {
        List<ProjectRunRecord> runs = new ArrayList<ProjectRunRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_runs where project_id = ? order by started_at desc limit ?");
            statement.setString(1, projectId);
            statement.setInt(2, limit);
            ResultSet rs = statement.executeQuery();
            try {
                while (rs.next()) {
                    runs.add(mapRun(rs));
                }
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return runs;
    }

    @Override
    public ProjectQuestionRecord saveQuestion(ProjectQuestionRecord question) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert or replace into project_questions (question_id, project_id, todo_id, asked_by, question, answer, status, created_at, answered_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, question.getQuestionId());
            statement.setString(2, question.getProjectId());
            statement.setString(3, question.getTodoId());
            statement.setString(4, question.getAskedBy());
            statement.setString(5, question.getQuestion());
            statement.setString(6, question.getAnswer());
            statement.setString(7, question.getStatus());
            statement.setLong(8, question.getCreatedAt());
            statement.setLong(9, question.getAnsweredAt());
            statement.executeUpdate();
            statement.close();
            return question;
        } finally {
            connection.close();
        }
    }

    @Override
    public ProjectQuestionRecord findQuestionById(String questionId) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_questions where question_id = ?");
            statement.setString(1, questionId);
            ResultSet rs = statement.executeQuery();
            try {
                return rs.next() ? mapQuestion(rs) : null;
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    @Override
    public List<ProjectQuestionRecord> listQuestions(String projectId, String status) throws Exception {
        List<ProjectQuestionRecord> questions = new ArrayList<ProjectQuestionRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement;
            if (status == null) {
                statement = connection.prepareStatement("select * from project_questions where project_id = ? order by created_at desc");
                statement.setString(1, projectId);
            } else {
                statement = connection.prepareStatement("select * from project_questions where project_id = ? and status = ? order by created_at desc");
                statement.setString(1, projectId);
                statement.setString(2, status);
            }
            ResultSet rs = statement.executeQuery();
            try {
                while (rs.next()) {
                    questions.add(mapQuestion(rs));
                }
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return questions;
    }

    @Override
    public ProjectEventRecord saveEvent(ProjectEventRecord event) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("insert into project_events (event_id, project_id, todo_id, event_type, actor, message, metadata_json, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)");
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getProjectId());
            statement.setString(3, event.getTodoId());
            statement.setString(4, event.getEventType());
            statement.setString(5, event.getActor());
            statement.setString(6, event.getMessage());
            statement.setString(7, event.getMetadataJson());
            statement.setLong(8, event.getCreatedAt());
            statement.executeUpdate();
            statement.close();
            return event;
        } finally {
            connection.close();
        }
    }

    @Override
    public List<ProjectEventRecord> listEvents(String projectId, int limit) throws Exception {
        List<ProjectEventRecord> events = new ArrayList<ProjectEventRecord>();
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from project_events where project_id = ? order by created_at desc limit ?");
            statement.setString(1, projectId);
            statement.setInt(2, limit);
            ResultSet rs = statement.executeQuery();
            try {
                while (rs.next()) {
                    events.add(mapEvent(rs));
                }
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
        return events;
    }

    private ProjectRecord findProject(String column, String value) throws Exception {
        Connection connection = database.openConnection();
        try {
            PreparedStatement statement = connection.prepareStatement("select * from projects where " + column + " = ?");
            statement.setString(1, value);
            ResultSet rs = statement.executeQuery();
            try {
                return rs.next() ? mapProject(rs) : null;
            } finally {
                rs.close();
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private ProjectRecord mapProject(ResultSet rs) throws Exception {
        ProjectRecord project = new ProjectRecord();
        project.setProjectId(rs.getString("project_id"));
        project.setSlug(rs.getString("slug"));
        project.setTitle(rs.getString("title"));
        project.setGoal(rs.getString("goal"));
        project.setStatus(rs.getString("status"));
        project.setCurrentTodoId(rs.getString("current_todo_id"));
        project.setCreatedAt(rs.getLong("created_at"));
        project.setUpdatedAt(rs.getLong("updated_at"));
        return project;
    }

    private ProjectTodoRecord mapTodo(ResultSet rs) throws Exception {
        ProjectTodoRecord todo = new ProjectTodoRecord();
        todo.setTodoId(rs.getString("todo_id"));
        todo.setProjectId(rs.getString("project_id"));
        todo.setParentTodoId(rs.getString("parent_todo_id"));
        todo.setTodoNo(rs.getString("todo_no"));
        todo.setTitle(rs.getString("title"));
        todo.setDescription(rs.getString("description"));
        todo.setStatus(rs.getString("status"));
        todo.setAssignedAgent(rs.getString("assigned_agent"));
        todo.setPriority(rs.getString("priority"));
        todo.setSortOrder(rs.getInt("sort_order"));
        todo.setCreatedAt(rs.getLong("created_at"));
        todo.setUpdatedAt(rs.getLong("updated_at"));
        todo.setFinishedAt(rs.getLong("finished_at"));
        return todo;
    }

    private ProjectRunRecord mapRun(ResultSet rs) throws Exception {
        ProjectRunRecord run = new ProjectRunRecord();
        run.setRunId(rs.getString("run_id"));
        run.setProjectId(rs.getString("project_id"));
        run.setTodoId(rs.getString("todo_id"));
        run.setAgentName(rs.getString("agent_name"));
        run.setSessionId(rs.getString("session_id"));
        run.setWorkDir(rs.getString("work_dir"));
        run.setModel(rs.getString("model"));
        run.setAllowedToolsJson(rs.getString("allowed_tools_json"));
        run.setLoadedMemoryFilesJson(rs.getString("loaded_memory_files_json"));
        run.setStatus(rs.getString("status"));
        run.setSummary(rs.getString("summary"));
        run.setStartedAt(rs.getLong("started_at"));
        run.setFinishedAt(rs.getLong("finished_at"));
        return run;
    }

    private ProjectQuestionRecord mapQuestion(ResultSet rs) throws Exception {
        ProjectQuestionRecord question = new ProjectQuestionRecord();
        question.setQuestionId(rs.getString("question_id"));
        question.setProjectId(rs.getString("project_id"));
        question.setTodoId(rs.getString("todo_id"));
        question.setAskedBy(rs.getString("asked_by"));
        question.setQuestion(rs.getString("question"));
        question.setAnswer(rs.getString("answer"));
        question.setStatus(rs.getString("status"));
        question.setCreatedAt(rs.getLong("created_at"));
        question.setAnsweredAt(rs.getLong("answered_at"));
        return question;
    }

    private ProjectEventRecord mapEvent(ResultSet rs) throws Exception {
        ProjectEventRecord event = new ProjectEventRecord();
        event.setEventId(rs.getString("event_id"));
        event.setProjectId(rs.getString("project_id"));
        event.setTodoId(rs.getString("todo_id"));
        event.setEventType(rs.getString("event_type"));
        event.setActor(rs.getString("actor"));
        event.setMessage(rs.getString("message"));
        event.setMetadataJson(rs.getString("metadata_json"));
        event.setCreatedAt(rs.getLong("created_at"));
        return event;
    }

    private void fillChildCounts(List<ProjectTodoRecord> todos) {
        for (ProjectTodoRecord parent : todos) {
            int total = 0;
            int done = 0;
            for (ProjectTodoRecord child : todos) {
                if (parent.getTodoId().equals(child.getParentTodoId())) {
                    total++;
                    if ("done".equals(child.getStatus())) {
                        done++;
                    }
                }
            }
            parent.setChildTotal(total);
            parent.setChildDone(done);
        }
    }
}
