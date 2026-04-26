package com.jimuqu.agent.web;

import com.jimuqu.agent.project.service.ProjectService;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class DashboardProjectsController {
    private final ProjectService projectService;

    public DashboardProjectsController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Mapping(value = "/api/todos", method = MethodType.GET)
    public Map<String, Object> projects() throws Exception {
        return DashboardResponse.ok(projectService.dashboard());
    }

    @Mapping(value = "/api/todos", method = MethodType.POST)
    public Map<String, Object> create(Context context) throws Exception {
        try {
            return DashboardResponse.ok(projectService.createProjectFromDashboard(body(context)));
        } catch (IllegalArgumentException e) {
            return badRequest(context, e);
        }
    }

    @Mapping(value = "/api/todos/{id}", method = MethodType.GET)
    public Map<String, Object> detail(String id, Context context) throws Exception {
        try {
            return DashboardResponse.ok(projectService.detail(id));
        } catch (IllegalStateException e) {
            return notFound(context, e);
        }
    }

    @Mapping(value = "/api/todos/{id}/items", method = MethodType.POST)
    public Map<String, Object> createTodo(String id, Context context) throws Exception {
        try {
            return DashboardResponse.ok(projectService.createTodoFromDashboard(id, body(context)));
        } catch (IllegalArgumentException e) {
            return badRequest(context, e);
        } catch (IllegalStateException e) {
            return notFound(context, e);
        }
    }

    @Mapping(value = "/api/todos/{id}/items/{todoId}/status", method = MethodType.POST)
    public Map<String, Object> updateTodoStatus(String id, String todoId, Context context) throws Exception {
        try {
            return DashboardResponse.ok(projectService.updateTodoStatus(id, todoId, body(context)));
        } catch (IllegalArgumentException e) {
            return badRequest(context, e);
        } catch (IllegalStateException e) {
            return notFound(context, e);
        }
    }

    private Map<String, Object> body(Context context) throws Exception {
        if (context == null || context.body() == null || context.body().trim().isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        try {
            return ONode.deserialize(ONode.ofJson(context.body()).toJson(), LinkedHashMap.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body", e);
        }
    }

    private Map<String, Object> badRequest(Context context, Exception e) {
        context.status(400);
        return DashboardResponse.error("BAD_REQUEST", e.getMessage());
    }

    private Map<String, Object> notFound(Context context, Exception e) {
        context.status(404);
        return DashboardResponse.error("NOT_FOUND", e.getMessage());
    }
}
