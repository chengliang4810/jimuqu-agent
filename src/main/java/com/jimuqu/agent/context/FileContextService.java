package com.jimuqu.agent.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.MemorySnapshot;
import com.jimuqu.agent.core.service.ContextService;
import com.jimuqu.agent.core.service.MemoryService;

import java.io.File;

/**
 * 基于文件系统拼装系统提示词的上下文服务。
 */
public class FileContextService implements ContextService {
    /**
     * 应用配置。
     */
    private final AppConfig appConfig;

    /**
     * 本地技能服务。
     */
    private final LocalSkillService localSkillService;

    /**
     * 长期记忆服务。
     */
    private final MemoryService memoryService;

    /**
     * 仓库根目录，用于读取项目根 AGENTS.md。
     */
    private final File repoRoot;

    /**
     * 构造文件上下文服务。
     */
    public FileContextService(AppConfig appConfig, LocalSkillService localSkillService, MemoryService memoryService, File repoRoot) {
        this.appConfig = appConfig;
        this.localSkillService = localSkillService;
        this.memoryService = memoryService;
        this.repoRoot = repoRoot;
        FileUtil.mkdir(appConfig.getRuntime().getContextDir());
    }

    /**
     * 组合 AGENTS、MEMORY、USER 与已启用技能内容。
     *
     * @param sourceKey 来源键
     * @return 系统提示词
     */
    @Override
    public String buildSystemPrompt(String sourceKey) {
        StringBuilder buffer = new StringBuilder();
        appendFile(buffer, contextFile("AGENTS.md"), new File(repoRoot, "AGENTS.md"), "Project Rules");
        appendMemorySnapshot(buffer);

        try {
            String skillPrompt = localSkillService.renderSkillIndexPrompt(sourceKey);
            if (StrUtil.isNotBlank(skillPrompt)) {
                buffer.append("\n\n").append(skillPrompt);
            }
        } catch (Exception e) {
            buffer.append("\n\n[Enabled Skills]\nFailed to load local skills: ").append(e.getMessage());
        }

        return buffer.toString().trim();
    }

    /**
     * 计算运行时上下文文件路径。
     */
    private File contextFile(String fileName) {
        return new File(appConfig.getRuntime().getContextDir(), fileName);
    }

    /**
     * 追加 MEMORY/USER 快照。
     */
    private void appendMemorySnapshot(StringBuilder buffer) {
        try {
            MemorySnapshot snapshot = memoryService.loadSnapshot();
            appendBlock(buffer, "Memory", snapshot.getMemoryText());
            appendBlock(buffer, "User", snapshot.getUserText());
        } catch (Exception e) {
            appendBlock(buffer, "Memory", "Failed to load memory snapshot: " + e.getMessage());
        }
    }

    /**
     * 按优先级追加上下文文件内容。
     */
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

    /**
     * 追加指定文本块。
     */
    private void appendBlock(StringBuilder buffer, String label, String content) {
        if (StrUtil.isBlank(content)) {
            return;
        }
        if (buffer.length() > 0) {
            buffer.append("\n\n");
        }
        buffer.append("[").append(label).append("]\n").append(content.trim());
    }
}
