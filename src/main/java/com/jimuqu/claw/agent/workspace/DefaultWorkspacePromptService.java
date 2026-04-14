package com.jimuqu.claw.agent.workspace;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.support.JsonSupport;
import org.noear.solon.Utils;
import org.noear.solon.core.util.ResourceUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultWorkspacePromptService implements WorkspacePromptService {
    private final WorkspaceLayout workspaceLayout;
    private final ClawProperties properties;

    public DefaultWorkspacePromptService(WorkspaceLayout workspaceLayout, ClawProperties properties) {
        this.workspaceLayout = workspaceLayout;
        this.properties = properties;
    }

    @Override
    public String buildSystemPrompt(SessionContext sessionContext) {
        StringBuilder prompt = new StringBuilder();
        appendSection(prompt, "System Base", readSystemBasePrompt());
        appendWorkspaceSection(prompt, "AGENTS.md", workspaceLayout.agentsFile());
        appendWorkspaceSection(prompt, "SOUL.md", workspaceLayout.soulFile());
        appendWorkspaceSection(prompt, "IDENTITY.md", workspaceLayout.identityFile());
        appendWorkspaceSection(prompt, "USER.md", workspaceLayout.userFile());
        appendWorkspaceSection(prompt, "TOOLS.md", workspaceLayout.toolsFile());
        appendWorkspaceSection(prompt, "HEARTBEAT.md", workspaceLayout.heartbeatFile());
        appendWorkspaceSection(prompt, "MEMORY.md", workspaceLayout.memoryFile());
        appendWorkspaceSection(prompt, "Daily Memory", workspaceLayout.dailyMemoryFile(LocalDate.now().toString()));
        appendSection(prompt, "Session Context", buildSessionContextBlock(sessionContext));
        return prompt.toString().trim();
    }

    private String readSystemBasePrompt() {
        String resource = properties.getRuntime().getSystemPromptResource();
        if (Utils.isBlank(resource)) {
            return "";
        }

        try {
            if (resource.startsWith("classpath:")) {
                return ResourceUtil.getResourceAsString(resource.substring("classpath:".length()));
            }

            return FileUtil.readString(resource, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt resource: " + resource, e);
        }
    }

    private void appendWorkspaceSection(StringBuilder prompt, String title, java.nio.file.Path file) {
        if (!file.toFile().exists()) {
            return;
        }

        appendSection(prompt, title, FileUtil.readString(file.toFile(), StandardCharsets.UTF_8).trim());
    }

    private void appendSection(StringBuilder prompt, String title, String content) {
        if (Utils.isBlank(content)) {
            return;
        }

        if (prompt.length() > 0) {
            prompt.append("\n\n");
        }

        prompt.append("## ").append(title).append('\n');
        prompt.append(content.trim());
    }

    private String buildSessionContextBlock(SessionContext sessionContext) {
        Map<String, Object> block = new LinkedHashMap<String, Object>();
        block.put("sessionId", sessionContext.getSessionId());
        block.put("platform", sessionContext.getPlatform());
        block.put("chatId", sessionContext.getChatId());
        block.put("threadId", sessionContext.getThreadId());
        block.put("userId", sessionContext.getUserId());
        block.put("messageId", sessionContext.getMessageId());
        block.put("workspaceRoot", sessionContext.getWorkspaceRoot());
        if (sessionContext.getMetadata() != null && !sessionContext.getMetadata().isEmpty()) {
            block.put("metadata", sessionContext.getMetadata());
        }

        return JsonSupport.toJson(block);
    }
}
