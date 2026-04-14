package com.jimuqu.claw.agent.workspace;

import cn.hutool.core.io.FileUtil;
import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Getter
public class WorkspaceLayout {
    private final Path root;

    public WorkspaceLayout(String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    public void initialize() {
        ensureDir(runtimeDir());
        ensureDir(sessionsDir());
        ensureDir(sessionAgentDir());
        ensureDir(runsDir());
        ensureDir(routesDir());
        ensureDir(dedupDir());
        ensureDir(jobsDir());
        ensureDir(processesDir());
        ensureDir(auditDir());
        ensureDir(memoryDir());
        ensureDir(skillsDir());
    }

    public Path agentsFile() {
        return root.resolve("AGENTS.md");
    }

    public Path soulFile() {
        return root.resolve("SOUL.md");
    }

    public Path identityFile() {
        return root.resolve("IDENTITY.md");
    }

    public Path userFile() {
        return root.resolve("USER.md");
    }

    public Path toolsFile() {
        return root.resolve("TOOLS.md");
    }

    public Path heartbeatFile() {
        return root.resolve("HEARTBEAT.md");
    }

    public Path memoryFile() {
        return root.resolve("MEMORY.md");
    }

    public Path memoryDir() {
        return root.resolve("memory");
    }

    public Path skillsDir() {
        return root.resolve("skills");
    }

    public Path runtimeDir() {
        return root.resolve("runtime");
    }

    public Path sessionsDir() {
        return runtimeDir().resolve("sessions");
    }

    public Path runsDir() {
        return runtimeDir().resolve("runs");
    }

    public Path routesDir() {
        return runtimeDir().resolve("routes");
    }

    public Path dedupDir() {
        return runtimeDir().resolve("dedup");
    }

    public Path jobsDir() {
        return runtimeDir().resolve("jobs");
    }

    public Path processesDir() {
        return runtimeDir().resolve("processes");
    }

    public Path auditDir() {
        return runtimeDir().resolve("audit");
    }

    public Path dailyMemoryFile(String date) {
        return memoryDir().resolve(date + ".md");
    }

    public Path sessionMetaFile(String sessionId) {
        return sessionsDir().resolve(sessionId + ".meta.json");
    }

    public Path sessionAgentDir() {
        return sessionsDir().resolve("agent");
    }

    public Path sessionTodoFile(String sessionId) {
        return sessionsDir().resolve(sessionId + ".todo.json");
    }

    public Path runFile(String runId) {
        return runsDir().resolve(runId + ".json");
    }

    public Path routeFile(String sessionId) {
        return routesDir().resolve(sessionId + ".json");
    }

    public Path dedupFile(String keyHash) {
        return dedupDir().resolve(keyHash + ".json");
    }

    public Path jobFile(String jobId) {
        return jobsDir().resolve(jobId + ".json");
    }

    public Path processFile(String processId) {
        return processesDir().resolve(processId + ".json");
    }

    private void ensureDir(Path path) {
        FileUtil.mkdir(path.toFile());
    }
}
