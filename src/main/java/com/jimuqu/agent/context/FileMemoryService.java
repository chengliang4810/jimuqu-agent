package com.jimuqu.agent.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.MemorySnapshot;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.support.constants.MemoryConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文件系统的长期记忆服务。
 */
public class FileMemoryService implements MemoryService {
    /**
     * 应用配置。
     */
    private final AppConfig appConfig;

    /**
     * 构造文件记忆服务。
     */
    public FileMemoryService(AppConfig appConfig) {
        this.appConfig = appConfig;
        FileUtil.mkdir(appConfig.getRuntime().getContextDir());
    }

    @Override
    public MemorySnapshot loadSnapshot() throws Exception {
        MemorySnapshot snapshot = new MemorySnapshot();
        snapshot.setMemoryText(read(MemoryConstants.TARGET_MEMORY));
        snapshot.setUserText(read(MemoryConstants.TARGET_USER));
        return snapshot;
    }

    @Override
    public String read(String target) throws Exception {
        File file = fileForTarget(target);
        if (!file.exists()) {
            return "";
        }
        return FileUtil.readUtf8String(file).trim();
    }

    @Override
    public String add(String target, String content) throws Exception {
        if (StrUtil.isBlank(content)) {
            return "记忆内容不能为空。";
        }

        List<String> entries = readEntries(target);
        String normalized = content.trim();
        if (!entries.contains(normalized)) {
            entries.add(normalized);
            writeEntries(target, entries);
        }
        return "已写入 " + normalizeTarget(target) + "。";
    }

    @Override
    public String replace(String target, String oldText, String newContent) throws Exception {
        if (StrUtil.isBlank(oldText) || StrUtil.isBlank(newContent)) {
            return "replace 需要 oldText 和 newContent。";
        }

        List<String> entries = readEntries(target);
        boolean replaced = false;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).contains(oldText.trim())) {
                entries.set(i, newContent.trim());
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            return "未找到可替换的记忆条目。";
        }

        writeEntries(target, entries);
        return "已更新 " + normalizeTarget(target) + "。";
    }

    @Override
    public String remove(String target, String matchText) throws Exception {
        if (StrUtil.isBlank(matchText)) {
            return "remove 需要 matchText。";
        }

        List<String> entries = readEntries(target);
        boolean removed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).contains(matchText.trim())) {
                entries.remove(i);
                removed = true;
            }
        }

        if (!removed) {
            return "未找到可删除的记忆条目。";
        }

        writeEntries(target, entries);
        return "已删除 " + normalizeTarget(target) + " 中的匹配条目。";
    }

    /**
     * 读取记忆条目列表。
     */
    private List<String> readEntries(String target) throws Exception {
        String raw = read(target);
        List<String> entries = new ArrayList<String>();
        if (StrUtil.isBlank(raw)) {
            return entries;
        }

        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.length() == 0) {
                continue;
            }
            if (trimmed.startsWith("- ")) {
                trimmed = trimmed.substring(2).trim();
            }
            if (trimmed.length() > 0) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    /**
     * 按统一格式写回记忆条目。
     */
    private void writeEntries(String target, List<String> entries) {
        StringBuilder buffer = new StringBuilder();
        for (String entry : entries) {
            if (buffer.length() > 0) {
                buffer.append(System.lineSeparator());
            }
            buffer.append("- ").append(entry);
        }
        FileUtil.writeUtf8String(buffer.toString(), fileForTarget(target));
    }

    /**
     * 解析目标文件。
     */
    private File fileForTarget(String target) {
        if (MemoryConstants.TARGET_USER.equalsIgnoreCase(target)) {
            return FileUtil.file(appConfig.getRuntime().getContextDir(), MemoryConstants.USER_FILE_NAME);
        }
        return FileUtil.file(appConfig.getRuntime().getContextDir(), MemoryConstants.MEMORY_FILE_NAME);
    }

    /**
     * 统一目标名输出。
     */
    private String normalizeTarget(String target) {
        return MemoryConstants.TARGET_USER.equalsIgnoreCase(target)
                ? MemoryConstants.TARGET_USER
                : MemoryConstants.TARGET_MEMORY;
    }
}
