package com.jimuqu.agent.project.model;

import java.util.Arrays;
import java.util.List;

public final class ProjectTodoStatus {
    public static final String TODO = "todo";
    public static final String IN_PROGRESS = "in_progress";
    public static final String WAITING_USER = "waiting_user";
    public static final String REVIEW = "review";
    public static final String DONE = "done";

    private ProjectTodoStatus() {
    }

    public static List<String> all() {
        return Arrays.asList(TODO, IN_PROGRESS, WAITING_USER, REVIEW, DONE);
    }

    public static String normalize(String status) {
        if (status == null) {
            return TODO;
        }
        String value = status.trim().toLowerCase();
        if ("inprocess".equals(value) || "in-process".equals(value) || "in_progress".equals(value)) {
            return IN_PROGRESS;
        }
        if ("in_review".equals(value) || "reviewing".equals(value)) {
            return REVIEW;
        }
        if ("waiting".equals(value) || "blocked".equals(value) || "waiting-user".equals(value)) {
            return WAITING_USER;
        }
        if (all().contains(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported todo status: " + status);
    }
}
