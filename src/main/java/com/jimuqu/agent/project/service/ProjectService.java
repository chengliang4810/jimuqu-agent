package com.jimuqu.agent.project.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.agent.AgentProfile;
import com.jimuqu.agent.agent.AgentProfileService;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.project.model.ProjectAgentRecord;
import com.jimuqu.agent.project.model.ProjectEventRecord;
import com.jimuqu.agent.project.model.ProjectQuestionRecord;
import com.jimuqu.agent.project.model.ProjectRecord;
import com.jimuqu.agent.project.model.ProjectRunRecord;
import com.jimuqu.agent.project.model.ProjectTodoRecord;
import com.jimuqu.agent.project.model.ProjectTodoStatus;
import com.jimuqu.agent.project.repository.ProjectRepository;
import com.jimuqu.agent.support.IdSupport;
import org.noear.snack4.ONode;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProjectService {
    private static final String PROJECT_MANAGER = "project-manager";
    private static final String CURRENT_KEY_PREFIX = "project.current.";

    private final AppConfig appConfig;
    private final ProjectRepository repository;
    private final AgentProfileService agentProfileService;
    private final GlobalSettingRepository globalSettingRepository;

    public ProjectService(AppConfig appConfig, ProjectRepository repository, AgentProfileService agentProfileService, GlobalSettingRepository globalSettingRepository) {
        this.appConfig = appConfig;
        this.repository = repository;
        this.agentProfileService = agentProfileService;
        this.globalSettingRepository = globalSettingRepository;
    }

    public String handleCommand(String sourceKey, String args) throws Exception {
        String text = StrUtil.nullToEmpty(args).trim();
        String[] parts = text.split("\\s+", 2);
        String action = parts.length == 0 || StrUtil.isBlank(parts[0]) ? "current" : parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        if ("init".equals(action)) return commandInit(sourceKey, rest);
        if ("goal".equals(action)) return commandGoal(sourceKey, rest);
        if ("list".equals(action)) return formatProjectList();
        if ("current".equals(action)) return formatCurrent(sourceKey);
        if ("use".equals(action) || "resume".equals(action)) return commandUse(sourceKey, rest);
        if ("board".equals(action)) return formatBoard(resolveOrCurrent(sourceKey, rest));
        if ("tree".equals(action)) return formatTree(resolveOrCurrent(sourceKey, rest));
        if ("todo".equals(action)) return handleTodoCommand(sourceKey, rest);
        if ("split".equals(action) || "auto".equals(action)) return splitTodo(sourceKey, rest);
        if ("assign".equals(action)) return assignTodo(sourceKey, rest);
        if ("run".equals(action)) return runTodo(sourceKey, rest);
        if ("review".equals(action)) return reviewTodo(sourceKey, rest);
        if ("done".equals(action)) return doneTodo(sourceKey, rest);
        if ("questions".equals(action)) return formatQuestions(resolveOrCurrent(sourceKey, rest));
        if ("answer".equals(action)) return answerQuestion(sourceKey, rest);
        return "Usage: /project init/goal/list/use/current/board/tree/todo add/split/assign/run/review/done/questions/answer/resume";
    }

    public ProjectRecord initProject(String args, String sourceKey) throws Exception {
        String title = StrUtil.blankToDefault(args, "Untitled Project").trim();
        String slug;
        String[] parts = title.split("\\s+", 2);
        if (parts.length > 1 && parts[0].matches("[A-Za-z0-9][A-Za-z0-9_-]{1,60}")) {
            slug = sanitizeSlug(parts[0]);
            title = parts[1].trim();
        } else {
            slug = sanitizeSlug(title);
        }
        title = requireText(title, "project title", 160);
        ProjectRecord existing = repository.findProjectBySlug(slug);
        if (existing != null) {
            setCurrent(sourceKey, existing.getProjectId());
            return existing;
        }
        long now = System.currentTimeMillis();
        ProjectRecord project = new ProjectRecord();
        project.setProjectId(IdSupport.newId());
        project.setSlug(slug);
        project.setTitle(title);
        project.setGoal(title);
        project.setStatus("active");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        repository.saveProject(project);
        attachProjectAgent(project.getProjectId(), PROJECT_MANAGER, "plan, split, assign, review, unblock");
        agentProfileService.ensureDefault(PROJECT_MANAGER, "Internal project manager for local projects.");
        setCurrent(sourceKey, project.getProjectId());
        event(project.getProjectId(), null, "project.init", "user", title, null);
        snapshot(project);
        return project;
    }

    private String commandInit(String sourceKey, String rest) throws Exception {
        ProjectRecord project = initProject(rest, sourceKey);
        return "Created project: " + project.getSlug() + "\nDir: " + projectDir(project).getAbsolutePath();
    }

    private String commandGoal(String sourceKey, String rest) throws Exception {
        ProjectRecord project = requireCurrent(sourceKey);
        project.setGoal(requireText(rest, "project goal", 2000));
        project.setUpdatedAt(System.currentTimeMillis());
        repository.saveProject(project);
        event(project.getProjectId(), null, "goal", "user", rest, null);
        snapshot(project);
        return "Updated project goal: " + project.getSlug();
    }

    private String commandUse(String sourceKey, String rest) throws Exception {
        ProjectRecord project = resolveProject(rest);
        setCurrent(sourceKey, project.getProjectId());
        return "Current project: " + project.getSlug();
    }

    public Map<String, Object> dashboard() throws Exception {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> projects = new ArrayList<Map<String, Object>>();
        for (ProjectRecord project : repository.listProjects()) projects.add(projectView(project, false));
        result.put("projects", projects);
        return result;
    }

    public Map<String, Object> detail(String projectIdOrSlug) throws Exception {
        ProjectRecord project = resolveProject(projectIdOrSlug);
        Map<String, Object> result = projectView(project, true);
        result.put("board", boardView(project));
        result.put("agents", agentsView(project));
        result.put("runs", runsView(project));
        result.put("questions", questionsView(project, null));
        result.put("events", eventsView(project));
        return result;
    }

    public Map<String, Object> createProjectFromDashboard(Map<String, Object> body) throws Exception {
        String slug = stringValue(body.get("slug"));
        if (StrUtil.isNotBlank(slug) && !slug.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,60}")) {
            throw new IllegalArgumentException("project slug must contain only letters, numbers, underscore or dash");
        }
        String title = requireText(StrUtil.blankToDefault(stringValue(body.get("title")), "Untitled Project"), "project title", 160);
        String goal = stringValue(body.get("goal"));
        ProjectRecord project = initProject((StrUtil.isBlank(slug) ? "" : slug + " ") + title, "dashboard");
        if (StrUtil.isNotBlank(goal)) {
            project.setGoal(requireText(goal, "project goal", 2000));
            project.setUpdatedAt(System.currentTimeMillis());
            repository.saveProject(project);
            snapshot(project);
        }
        return projectView(project, true);
    }

    public Map<String, Object> createTodoFromDashboard(String projectIdOrSlug, Map<String, Object> body) throws Exception {
        ProjectRecord project = resolveProject(projectIdOrSlug);
        ProjectTodoRecord todo = addTodo(project, stringValue(body.get("title")), stringValue(body.get("description")), stringValue(body.get("parent_todo_id")), StrUtil.blankToDefault(stringValue(body.get("priority")), "normal"));
        return todoView(todo);
    }

    public Map<String, Object> updateTodoStatus(String projectIdOrSlug, String todoId, Map<String, Object> body) throws Exception {
        ProjectRecord project = resolveProject(projectIdOrSlug);
        ProjectTodoRecord todo = requireTodo(project, todoId);
        moveTodo(project, todo, ProjectTodoStatus.normalize(stringValue(body.get("status"))), "dashboard");
        snapshot(project);
        return todoView(todo);
    }

    private String handleTodoCommand(String sourceKey, String rest) throws Exception {
        String[] parts = StrUtil.nullToEmpty(rest).trim().split("\\s+", 2);
        String action = parts.length == 0 || StrUtil.isBlank(parts[0]) ? "add" : parts[0].toLowerCase(Locale.ROOT);
        if (!"add".equals(action)) return "Usage: /project todo add <title>";
        ProjectRecord project = requireCurrent(sourceKey);
        ProjectTodoRecord todo = addTodo(project, parts.length > 1 ? parts[1] : "", "", null, "normal");
        project.setCurrentTodoId(todo.getTodoId());
        project.setUpdatedAt(System.currentTimeMillis());
        repository.saveProject(project);
        snapshot(project);
        return "Added todo: " + todo.getTodoNo() + " " + todo.getTitle();
    }

    private ProjectTodoRecord addTodo(ProjectRecord project, String title, String description, String parentTodoId, String priority) throws Exception {
        title = requireText(title, "todo title", 200);
        description = trimToMax(description, 4000, "todo description");
        priority = trimToMax(StrUtil.blankToDefault(priority, "normal"), 40, "todo priority");
        ProjectTodoRecord parent = null;
        if (StrUtil.isNotBlank(parentTodoId)) {
            parent = requireTodo(project, parentTodoId);
        }
        long now = System.currentTimeMillis();
        ProjectTodoRecord todo = new ProjectTodoRecord();
        todo.setTodoId(IdSupport.newId());
        todo.setProjectId(project.getProjectId());
        todo.setParentTodoId(parent == null ? null : parent.getTodoId());
        todo.setTodoNo(nextTodoNo(project, parent == null ? null : parent.getTodoId()));
        todo.setTitle(title);
        todo.setDescription(StrUtil.nullToEmpty(description));
        todo.setStatus(ProjectTodoStatus.TODO);
        todo.setAssignedAgent(PROJECT_MANAGER);
        todo.setPriority(priority);
        todo.setSortOrder(repository.nextSortOrder(project.getProjectId()));
        todo.setCreatedAt(now);
        todo.setUpdatedAt(now);
        repository.saveTodo(todo);
        event(project.getProjectId(), todo.getTodoId(), "todo.add", "user", todo.getTitle(), null);
        snapshot(project);
        return todo;
    }

    private String splitTodo(String sourceKey, String rest) throws Exception {
        ProjectRecord project = requireCurrent(sourceKey);
        ProjectTodoRecord parent = null;
        String itemsText = rest;
        String[] parts = StrUtil.nullToEmpty(rest).split("\\s+", 2);
        if (parts.length > 0 && StrUtil.isNotBlank(parts[0])) {
            ProjectTodoRecord candidate = findTodo(project, parts[0]);
            if (candidate != null) {
                parent = candidate;
                itemsText = parts.length > 1 ? parts[1] : "";
            }
        }
        if (parent == null && StrUtil.isNotBlank(project.getCurrentTodoId())) parent = repository.findTodoById(project.getCurrentTodoId());
        List<String> items = splitItems(itemsText);
        if (items.isEmpty()) {
            items.add("Clarify requirements and acceptance criteria");
            items.add("Implement core behavior");
            items.add("Verify and summarize results");
        }
        for (String item : items) addTodo(project, item, "Generated by project-manager", parent == null ? null : parent.getTodoId(), "normal");
        snapshot(project);
        return "Split todo: " + items.size() + " items" + (parent == null ? "" : ", parent=" + parent.getTodoNo());
    }

    private String assignTodo(String sourceKey, String rest) throws Exception {
        ProjectRecord project = requireCurrent(sourceKey);
        String[] parts = StrUtil.nullToEmpty(rest).split("\\s+", 2);
        if (parts.length < 2 || StrUtil.hasBlank(parts[0], parts[1])) return "Usage: /project assign <todo-no|todo-id> <agent-name>";
        ProjectTodoRecord todo = requireTodo(project, parts[0]);
        String agentName = requireText(parts[1], "agent name", 80);
        agentProfileService.ensureDefault(agentName, "Project worker agent.");
        attachProjectAgent(project.getProjectId(), agentName, "assigned worker");
        todo.setAssignedAgent(agentName);
        todo.setUpdatedAt(System.currentTimeMillis());
        repository.saveTodo(todo);
        event(project.getProjectId(), todo.getTodoId(), "todo.assign", "project-manager", agentName, null);
        snapshot(project);
        return "Assigned: " + todo.getTodoNo() + " -> " + agentName;
    }

    private String runTodo(String sourceKey, String rest) throws Exception {
        ProjectRecord project = requireCurrent(sourceKey);
        ProjectTodoRecord todo = StrUtil.isBlank(rest) ? nextRunnableTodo(project) : requireTodo(project, rest);
        if (todo == null) return "No runnable todo.";
        if (hasSecretLikeText(todo)) {
            moveTodo(project, todo, ProjectTodoStatus.WAITING_USER, PROJECT_MANAGER);
            ProjectQuestionRecord question = ask(project, todo, PROJECT_MANAGER, "This todo may need an API Key, token, password, account, or other secret. Please handle it manually or provide non-sensitive instructions. User: " + StrUtil.blankToDefault(todo.getAssignedAgent(), PROJECT_MANAGER));
            snapshot(project);
            return "Moved to waiting_user: " + todo.getTodoNo() + "\nQuestion: " + question.getQuestionId() + " " + question.getQuestion();
        }
        moveTodo(project, todo, ProjectTodoStatus.IN_PROGRESS, StrUtil.blankToDefault(todo.getAssignedAgent(), PROJECT_MANAGER));
        ProjectRunRecord run = buildRun(project, todo);
        run.setStatus("completed");
        run.setSummary("v1 local serial run built context and moved todo to review. Worker loop integration is deferred.");
        run.setFinishedAt(System.currentTimeMillis());
        repository.saveRun(run);
        moveTodo(project, todo, ProjectTodoStatus.REVIEW, StrUtil.blankToDefault(todo.getAssignedAgent(), PROJECT_MANAGER));
        snapshot(project);
        return "Ran: " + todo.getTodoNo() + " -> review\nrun=" + run.getRunId() + "\ncontext=" + run.getLoadedMemoryFilesJson();
    }

    private String reviewTodo(String sourceKey, String rest) throws Exception {
        ProjectRecord project = requireCurrent(sourceKey);
        String[] parts = StrUtil.nullToEmpty(rest).split("\\s+", 3);
        if (parts.length == 0 || StrUtil.isBlank(parts[0])) return "Usage: /project review <todo-no|todo-id> [pass|fail] [reason]";
        ProjectTodoRecord todo = requireTodo(project, parts[0]);
        String decision = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "pass";
        if ("pass".equals(decision) || "ok".equals(decision)) {
            markDone(project, todo, PROJECT_MANAGER);
            snapshot(project);
            return "review passed, done: " + todo.getTodoNo();
        }
        String reason = parts.length > 2 ? parts[2] : "review failed and needs a fix";
        moveTodo(project, todo, ProjectTodoStatus.TODO, PROJECT_MANAGER);
        ProjectTodoRecord fix = addTodo(project, "Fix " + todo.getTitle(), reason, todo.getTodoId(), todo.getPriority());
        snapshot(project);
        return "review failed, created fix todo: " + fix.getTodoNo();
    }

    private String doneTodo(String sourceKey, String rest) throws Exception {
        ProjectRecord project = requireCurrent(sourceKey);
        ProjectTodoRecord todo = requireTodo(project, rest);
        markDone(project, todo, "user");
        snapshot(project);
        return "Done: " + todo.getTodoNo();
    }

    private String answerQuestion(String sourceKey, String rest) throws Exception {
        ProjectRecord project = requireCurrent(sourceKey);
        String[] parts = StrUtil.nullToEmpty(rest).split("\\s+", 2);
        if (parts.length < 2 || StrUtil.hasBlank(parts[0], parts[1])) return "Usage: /project answer <question-id> <answer>";
        ProjectQuestionRecord question = repository.findQuestionById(parts[0]);
        if (question == null || !project.getProjectId().equals(question.getProjectId())) return "Question not found: " + parts[0];
        question.setAnswer(parts[1]);
        question.setStatus("answered");
        question.setAnsweredAt(System.currentTimeMillis());
        repository.saveQuestion(question);
        if (StrUtil.isNotBlank(question.getTodoId())) {
            ProjectTodoRecord todo = repository.findTodoById(question.getTodoId());
            if (todo != null && ProjectTodoStatus.WAITING_USER.equals(todo.getStatus())) moveTodo(project, todo, ProjectTodoStatus.TODO, PROJECT_MANAGER);
        }
        event(project.getProjectId(), question.getTodoId(), "question.answer", "user", parts[1], null);
        snapshot(project);
        return "Answer saved and related todo resumed.";
    }

    private ProjectRunRecord buildRun(ProjectRecord project, ProjectTodoRecord todo) throws Exception {
        long now = System.currentTimeMillis();
        AgentProfile profile = agentProfileService.ensureDefault(StrUtil.blankToDefault(todo.getAssignedAgent(), PROJECT_MANAGER), "Project worker agent.");
        File dir = projectDir(project);
        List<String> memoryFiles = new ArrayList<String>();
        memoryFiles.add(new File(dir, "PROJECT.md").getAbsolutePath());
        memoryFiles.add(new File(dir, "MEMORY.md").getAbsolutePath());
        memoryFiles.add(new File(dir, "PROJECT_STATE.md").getAbsolutePath());
        memoryFiles.add(new File(new File(dir, "agents"), profile.getAgentName() + File.separator + "MEMORY.md").getAbsolutePath());
        memoryFiles.add(new File(new File(dir, "todos"), todo.getTodoNo() + ".md").getAbsolutePath());
        ProjectRunRecord run = new ProjectRunRecord();
        run.setRunId(IdSupport.newId());
        run.setProjectId(project.getProjectId());
        run.setTodoId(todo.getTodoId());
        run.setAgentName(profile.getAgentName());
        run.setSessionId("project-" + project.getSlug() + "-" + todo.getTodoNo());
        run.setWorkDir(dir.getAbsolutePath());
        run.setModel(StrUtil.blankToDefault(profile.getModel(), "default"));
        run.setAllowedToolsJson(StrUtil.blankToDefault(profile.getAllowedToolsJson(), "[]"));
        run.setLoadedMemoryFilesJson(ONode.serialize(memoryFiles));
        run.setStatus("running");
        run.setStartedAt(now);
        run.setFinishedAt(0L);
        repository.saveRun(run);
        event(project.getProjectId(), todo.getTodoId(), "run.start", profile.getAgentName(), run.getRunId(), null);
        return run;
    }

    private void markDone(ProjectRecord project, ProjectTodoRecord todo, String actor) throws Exception {
        List<ProjectTodoRecord> children = repository.listChildTodos(todo.getTodoId());
        for (ProjectTodoRecord child : children) {
            if (!ProjectTodoStatus.DONE.equals(child.getStatus())) throw new IllegalStateException("Child todos must be done first: " + todo.getTodoNo());
        }
        moveTodo(project, todo, ProjectTodoStatus.DONE, actor);
    }

    private void moveTodo(ProjectRecord project, ProjectTodoRecord todo, String status, String actor) throws Exception {
        todo.setStatus(ProjectTodoStatus.normalize(status));
        todo.setUpdatedAt(System.currentTimeMillis());
        todo.setFinishedAt(ProjectTodoStatus.DONE.equals(todo.getStatus()) ? todo.getUpdatedAt() : 0L);
        repository.saveTodo(todo);
        project.setCurrentTodoId(todo.getTodoId());
        project.setUpdatedAt(todo.getUpdatedAt());
        repository.saveProject(project);
        event(project.getProjectId(), todo.getTodoId(), "todo.status", actor, todo.getStatus(), null);
    }

    private ProjectQuestionRecord ask(ProjectRecord project, ProjectTodoRecord todo, String askedBy, String text) throws Exception {
        long now = System.currentTimeMillis();
        ProjectQuestionRecord question = new ProjectQuestionRecord();
        question.setQuestionId(IdSupport.newId());
        question.setProjectId(project.getProjectId());
        question.setTodoId(todo == null ? null : todo.getTodoId());
        question.setAskedBy(askedBy);
        question.setQuestion(text);
        question.setStatus("open");
        question.setCreatedAt(now);
        repository.saveQuestion(question);
        event(project.getProjectId(), question.getTodoId(), "question.open", askedBy, text, null);
        return question;
    }

    private void attachProjectAgent(String projectId, String agentName, String roleHint) throws Exception {
        long now = System.currentTimeMillis();
        ProjectAgentRecord agent = new ProjectAgentRecord();
        agent.setProjectId(projectId);
        agent.setAgentName(agentName);
        agent.setRoleHint(roleHint);
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        repository.saveAgent(agent);
    }

    private void event(String projectId, String todoId, String type, String actor, String message, String metadataJson) throws Exception {
        ProjectEventRecord event = new ProjectEventRecord();
        event.setEventId(IdSupport.newId());
        event.setProjectId(projectId);
        event.setTodoId(todoId);
        event.setEventType(type);
        event.setActor(actor);
        event.setMessage(message);
        event.setMetadataJson(metadataJson);
        event.setCreatedAt(System.currentTimeMillis());
        repository.saveEvent(event);
    }

    private ProjectTodoRecord nextRunnableTodo(ProjectRecord project) throws Exception {
        for (ProjectTodoRecord todo : repository.listTodos(project.getProjectId())) {
            if (ProjectTodoStatus.TODO.equals(todo.getStatus()) || ProjectTodoStatus.IN_PROGRESS.equals(todo.getStatus())) return todo;
        }
        return null;
    }

    private ProjectTodoRecord requireTodo(ProjectRecord project, String todoIdOrNo) throws Exception {
        ProjectTodoRecord todo = findTodo(project, todoIdOrNo);
        if (todo == null) throw new IllegalStateException("Todo not found: " + todoIdOrNo);
        return todo;
    }

    private ProjectTodoRecord findTodo(ProjectRecord project, String todoIdOrNo) throws Exception {
        if (StrUtil.isBlank(todoIdOrNo)) return null;
        ProjectTodoRecord todo = repository.findTodoByNo(project.getProjectId(), todoIdOrNo.trim());
        if (todo != null) return todo;
        todo = repository.findTodoById(todoIdOrNo.trim());
        return todo != null && project.getProjectId().equals(todo.getProjectId()) ? todo : null;
    }

    private ProjectRecord requireCurrent(String sourceKey) throws Exception {
        ProjectRecord project = currentProject(sourceKey);
        if (project == null) throw new IllegalStateException("No current project. Use /project init <slug> <title> first.");
        return project;
    }

    private ProjectRecord currentProject(String sourceKey) throws Exception {
        String id = globalSettingRepository.get(CURRENT_KEY_PREFIX + sourceKey);
        if (StrUtil.isNotBlank(id)) {
            ProjectRecord project = repository.findProjectById(id);
            if (project != null) return project;
        }
        List<ProjectRecord> projects = repository.listProjects();
        return projects.isEmpty() ? null : projects.get(0);
    }

    private ProjectRecord resolveOrCurrent(String sourceKey, String projectIdOrSlug) throws Exception {
        return StrUtil.isBlank(projectIdOrSlug) ? requireCurrent(sourceKey) : resolveProject(projectIdOrSlug);
    }

    private ProjectRecord resolveProject(String projectIdOrSlug) throws Exception {
        if (StrUtil.isBlank(projectIdOrSlug)) throw new IllegalArgumentException("project slug or id is required");
        ProjectRecord project = repository.findProjectBySlug(projectIdOrSlug.trim());
        if (project == null) project = repository.findProjectById(projectIdOrSlug.trim());
        if (project == null) throw new IllegalStateException("Project not found: " + projectIdOrSlug);
        return project;
    }

    private void setCurrent(String sourceKey, String projectId) throws Exception {
        globalSettingRepository.set(CURRENT_KEY_PREFIX + sourceKey, projectId);
    }

    private String formatProjectList() throws Exception {
        List<ProjectRecord> projects = repository.listProjects();
        if (projects.isEmpty()) return "No projects. Use /project init <slug> <title>.";
        StringBuilder builder = new StringBuilder("Projects:");
        for (ProjectRecord project : projects) builder.append("\n- ").append(project.getSlug()).append(": ").append(project.getTitle()).append(" [").append(project.getStatus()).append("]");
        return builder.toString();
    }

    private String formatCurrent(String sourceKey) throws Exception {
        ProjectRecord project = currentProject(sourceKey);
        return project == null ? "No current project." : "Current project: " + project.getSlug() + "\nGoal: " + StrUtil.blankToDefault(project.getGoal(), "not set");
    }

    private String formatBoard(ProjectRecord project) throws Exception {
        Map<String, List<ProjectTodoRecord>> board = groupTodos(project);
        StringBuilder builder = new StringBuilder("Project board: ").append(project.getSlug());
        for (String status : ProjectTodoStatus.all()) {
            List<ProjectTodoRecord> todos = board.get(status);
            builder.append("\n\n# ").append(status).append(" (").append(todos.size()).append(")");
            for (ProjectTodoRecord todo : todos) {
                builder.append("\n- ").append(todo.getTodoNo()).append(" ").append(todo.getTitle()).append(" @").append(StrUtil.blankToDefault(todo.getAssignedAgent(), PROJECT_MANAGER)).append(" [").append(StrUtil.blankToDefault(todo.getPriority(), "normal")).append("]");
                if (todo.getChildTotal() > 0) builder.append(" children ").append(todo.getChildDone()).append("/").append(todo.getChildTotal());
            }
        }
        return builder.toString();
    }

    private String formatTree(ProjectRecord project) throws Exception {
        List<ProjectTodoRecord> todos = repository.listTodos(project.getProjectId());
        StringBuilder builder = new StringBuilder("Project tree: ").append(project.getSlug());
        appendTree(builder, todos, null, "");
        return builder.toString();
    }

    private void appendTree(StringBuilder builder, List<ProjectTodoRecord> todos, String parentId, String indent) {
        for (ProjectTodoRecord todo : todos) {
            if ((parentId == null && StrUtil.isBlank(todo.getParentTodoId())) || (parentId != null && parentId.equals(todo.getParentTodoId()))) {
                builder.append("\n").append(indent).append("- ").append(todo.getTodoNo()).append(" [").append(todo.getStatus()).append("] ").append(todo.getTitle());
                appendTree(builder, todos, todo.getTodoId(), indent + "  ");
            }
        }
    }

    private String formatQuestions(ProjectRecord project) throws Exception {
        List<ProjectQuestionRecord> questions = repository.listQuestions(project.getProjectId(), "open");
        if (questions.isEmpty()) return "No open questions.";
        StringBuilder builder = new StringBuilder("Open questions:");
        for (ProjectQuestionRecord question : questions) builder.append("\n- ").append(question.getQuestionId()).append(" by ").append(question.getAskedBy()).append(": ").append(question.getQuestion());
        return builder.toString();
    }

    private Map<String, List<ProjectTodoRecord>> groupTodos(ProjectRecord project) throws Exception {
        Map<String, List<ProjectTodoRecord>> board = new LinkedHashMap<String, List<ProjectTodoRecord>>();
        for (String status : ProjectTodoStatus.all()) board.put(status, new ArrayList<ProjectTodoRecord>());
        for (ProjectTodoRecord todo : repository.listTodos(project.getProjectId())) board.get(ProjectTodoStatus.normalize(todo.getStatus())).add(todo);
        return board;
    }

    private Map<String, Object> projectView(ProjectRecord project, boolean includeCounts) throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", project.getProjectId());
        map.put("slug", project.getSlug());
        map.put("title", project.getTitle());
        map.put("goal", project.getGoal());
        map.put("status", project.getStatus());
        map.put("current_todo_id", project.getCurrentTodoId());
        map.put("updated_at", iso(project.getUpdatedAt()));
        map.put("created_at", iso(project.getCreatedAt()));
        map.put("dir", projectDir(project).getAbsolutePath());
        if (includeCounts) {
            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            for (String status : ProjectTodoStatus.all()) counts.put(status, 0);
            for (ProjectTodoRecord todo : repository.listTodos(project.getProjectId())) {
                String status = ProjectTodoStatus.normalize(todo.getStatus());
                counts.put(status, counts.get(status) + 1);
            }
            map.put("counts", counts);
        }
        return map;
    }

    private List<Map<String, Object>> boardView(ProjectRecord project) throws Exception {
        List<Map<String, Object>> columns = new ArrayList<Map<String, Object>>();
        Map<String, List<ProjectTodoRecord>> board = groupTodos(project);
        for (String status : ProjectTodoStatus.all()) {
            Map<String, Object> column = new LinkedHashMap<String, Object>();
            List<Map<String, Object>> cards = new ArrayList<Map<String, Object>>();
            for (ProjectTodoRecord todo : board.get(status)) cards.add(todoView(todo));
            column.put("status", status);
            column.put("title", statusLabel(status));
            column.put("count", cards.size());
            column.put("todos", cards);
            columns.add(column);
        }
        return columns;
    }

    private List<Map<String, Object>> agentsView(ProjectRecord project) throws Exception {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ProjectAgentRecord agent : repository.listAgents(project.getProjectId())) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("agent_name", agent.getAgentName());
            map.put("role_hint", agent.getRoleHint());
            result.add(map);
        }
        return result;
    }

    private String statusLabel(String status) {
        if (ProjectTodoStatus.TODO.equals(status)) {
            return "待处理";
        }
        if (ProjectTodoStatus.IN_PROGRESS.equals(status)) {
            return "进行中";
        }
        if (ProjectTodoStatus.WAITING_USER.equals(status)) {
            return "等待用户";
        }
        if (ProjectTodoStatus.REVIEW.equals(status)) {
            return "待复核";
        }
        if (ProjectTodoStatus.DONE.equals(status)) {
            return "已完成";
        }
        return status;
    }

    private List<Map<String, Object>> runsView(ProjectRecord project) throws Exception {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ProjectRunRecord run : repository.listRuns(project.getProjectId(), 20)) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("id", run.getRunId());
            map.put("todo_id", run.getTodoId());
            map.put("agent_name", run.getAgentName());
            map.put("status", run.getStatus());
            map.put("summary", run.getSummary());
            map.put("started_at", iso(run.getStartedAt()));
            map.put("finished_at", run.getFinishedAt() <= 0 ? null : iso(run.getFinishedAt()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> questionsView(ProjectRecord project, String status) throws Exception {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ProjectQuestionRecord question : repository.listQuestions(project.getProjectId(), status)) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("id", question.getQuestionId());
            map.put("todo_id", question.getTodoId());
            map.put("asked_by", question.getAskedBy());
            map.put("question", question.getQuestion());
            map.put("answer", question.getAnswer());
            map.put("status", question.getStatus());
            map.put("created_at", iso(question.getCreatedAt()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> eventsView(ProjectRecord project) throws Exception {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ProjectEventRecord event : repository.listEvents(project.getProjectId(), 50)) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("id", event.getEventId());
            map.put("todo_id", event.getTodoId());
            map.put("type", event.getEventType());
            map.put("actor", event.getActor());
            map.put("message", event.getMessage());
            map.put("created_at", iso(event.getCreatedAt()));
            result.add(map);
        }
        return result;
    }

    private Map<String, Object> todoView(ProjectTodoRecord todo) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", todo.getTodoId());
        map.put("project_id", todo.getProjectId());
        map.put("parent_todo_id", todo.getParentTodoId());
        map.put("no", todo.getTodoNo());
        map.put("title", todo.getTitle());
        map.put("description", todo.getDescription());
        map.put("status", todo.getStatus());
        map.put("assigned_agent", todo.getAssignedAgent());
        map.put("priority", todo.getPriority());
        map.put("child_total", todo.getChildTotal());
        map.put("child_done", todo.getChildDone());
        map.put("updated_at", iso(todo.getUpdatedAt()));
        return map;
    }

    private void snapshot(ProjectRecord project) throws Exception {
        File dir = projectDir(project);
        File todosDir = new File(dir, "todos");
        File agentsDir = new File(dir, "agents");
        FileUtil.mkdir(todosDir);
        FileUtil.mkdir(agentsDir);
        List<ProjectTodoRecord> todos = repository.listTodos(project.getProjectId());
        FileUtil.writeUtf8String("# " + project.getTitle() + "\n\nSlug: " + project.getSlug() + "\nStatus: " + project.getStatus() + "\n\n## Goal\n" + StrUtil.blankToDefault(project.getGoal(), "") + "\n", new File(dir, "PROJECT.md"));
        FileUtil.writeUtf8String("# Project Memory\n\n", new File(dir, "MEMORY.md"));
        FileUtil.writeUtf8String(formatBoard(project) + "\n", new File(dir, "PROJECT_STATE.md"));
        for (ProjectTodoRecord todo : todos) {
            String content = "# " + todo.getTodoNo() + " " + todo.getTitle() + "\n\nStatus: " + todo.getStatus() + "\nAgent: " + StrUtil.blankToDefault(todo.getAssignedAgent(), PROJECT_MANAGER) + "\nPriority: " + StrUtil.blankToDefault(todo.getPriority(), "normal") + "\nParent: " + StrUtil.blankToDefault(todo.getParentTodoId(), "") + "\n\n## Description\n" + StrUtil.blankToDefault(todo.getDescription(), "") + "\n";
            FileUtil.writeUtf8String(content, new File(todosDir, todo.getTodoNo() + ".md"));
        }
        for (ProjectAgentRecord agent : repository.listAgents(project.getProjectId())) {
            File agentDir = new File(agentsDir, agent.getAgentName());
            FileUtil.mkdir(agentDir);
            AgentProfile profile = agentProfileService.findByName(agent.getAgentName());
            String memory = profile == null ? "" : StrUtil.blankToDefault(profile.getMemory(), "");
            FileUtil.writeUtf8String("# " + agent.getAgentName() + " Memory\n\n" + memory + "\n", new File(agentDir, "MEMORY.md"));
        }
    }

    private File projectDir(ProjectRecord project) {
        return FileUtil.file(appConfig.getRuntime().getHome(), "projects", project.getSlug());
    }

    private String nextTodoNo(ProjectRecord project, String parentTodoId) throws Exception {
        if (StrUtil.isBlank(parentTodoId)) {
            int next = repository.nextSortOrder(project.getProjectId());
            return String.format(Locale.ROOT, "TODO-%03d", Integer.valueOf(next));
        }
        ProjectTodoRecord parent = repository.findTodoById(parentTodoId);
        int next = repository.listChildTodos(parentTodoId).size() + 1;
        return (parent == null ? "TODO" : parent.getTodoNo()) + "." + next;
    }

    private List<String> splitItems(String text) {
        List<String> items = new ArrayList<String>();
        String normalized = StrUtil.nullToEmpty(text).replace('\r', '\n').replace(';', '\n');
        String[] parts = normalized.split("\\n");
        for (String part : parts) {
            String item = part.replaceAll("^\\s*[-*0-9.]+\\s*", "").trim();
            if (StrUtil.isNotBlank(item)) items.add(item);
        }
        return items;
    }

    private boolean hasSecretLikeText(ProjectTodoRecord todo) {
        String text = (StrUtil.nullToEmpty(todo.getTitle()) + "\n" + StrUtil.nullToEmpty(todo.getDescription())).toLowerCase(Locale.ROOT);
        return text.contains("api key") || text.contains("apikey") || text.contains("secret") || text.contains("token") || text.contains("password") || text.contains("passwd") || text.contains("key");
    }

    private String sanitizeSlug(String text) {
        String slug = StrUtil.nullToEmpty(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (StrUtil.isBlank(slug)) slug = "project-" + System.currentTimeMillis();
        return slug.length() > 64 ? slug.substring(0, 64) : slug;
    }

    private String requireText(String value, String field, int maxLength) {
        String text = StrUtil.nullToEmpty(value).trim();
        if (StrUtil.isBlank(text)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return trimToMax(text, maxLength, field);
    }

    private String trimToMax(String value, int maxLength, String field) {
        String text = StrUtil.nullToEmpty(value).trim();
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return text;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String iso(long epochMillis) {
        if (epochMillis <= 0) return null;
        return DateUtil.format(new Date(epochMillis), "yyyy-MM-dd'T'HH:mm:ssXXX");
    }
}
