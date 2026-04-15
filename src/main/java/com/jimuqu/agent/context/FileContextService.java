package com.jimuqu.agent.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.ContextService;

import java.io.File;

public class FileContextService implements ContextService {
    private final AppConfig appConfig;
    private final LocalSkillService localSkillService;
    private final File repoRoot;

    public FileContextService(AppConfig appConfig, LocalSkillService localSkillService, File repoRoot) {
        this.appConfig = appConfig;
        this.localSkillService = localSkillService;
        this.repoRoot = repoRoot;
        FileUtil.mkdir(appConfig.getRuntime().getContextDir());
    }

    public String buildSystemPrompt(String sourceKey) {
        StringBuilder buffer = new StringBuilder();
        appendFile(buffer, contextFile("AGENTS.md"), new File(repoRoot, "AGENTS.md"), "Project Rules");
        appendFile(buffer, contextFile("MEMORY.md"), null, "Memory");
        appendFile(buffer, contextFile("USER.md"), null, "User");

        try {
            String skillPrompt = localSkillService.renderEnabledSkillPrompts(sourceKey);
            if (StrUtil.isNotBlank(skillPrompt)) {
                buffer.append("\n\n[Enabled Skills]\n").append(skillPrompt);
            }
        } catch (Exception e) {
            buffer.append("\n\n[Enabled Skills]\nFailed to load local skills: ").append(e.getMessage());
        }

        return buffer.toString().trim();
    }

    private File contextFile(String fileName) {
        return new File(appConfig.getRuntime().getContextDir(), fileName);
    }

    private void appendFile(StringBuilder buffer, File preferred, File fallback, String label) {
        File chosen = preferred.exists() ? preferred : fallback;
        if (chosen == null || !chosen.exists()) {
            return;
        }

        String content = FileUtil.readUtf8String(chosen).trim();
        if (StrUtil.isBlank(content)) {
            return;
        }

        if (buffer.length() > 0) {
            buffer.append("\n\n");
        }

        buffer.append("[").append(label).append("]\n").append(content);
    }
}
