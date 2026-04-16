package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import org.noear.solon.ai.annotation.ToolMapping;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 文件工具。
 */
public class FileTools {
    /**
     * checkpoint 服务。
     */
    private final CheckpointService checkpointService;

    /**
     * 会话仓储。
     */
    private final SessionRepository sessionRepository;

    /**
     * 当前来源键。
     */
    private final String sourceKey;

    /**
     * 构造文件工具。
     */
    public FileTools(CheckpointService checkpointService, SessionRepository sessionRepository, String sourceKey) {
        this.checkpointService = checkpointService;
        this.sessionRepository = sessionRepository;
        this.sourceKey = sourceKey;
    }

    @ToolMapping(name = "read_file", description = "Read a UTF-8 text file from disk by absolute or relative path.")
    public String readFile(String path) {
        return FileUtil.readUtf8String(FileUtil.file(path));
    }

    @ToolMapping(name = "write_file", description = "Write UTF-8 text content to a file path, creating parent directories when needed.")
    public String writeFile(String path, String content) throws Exception {
        File file = FileUtil.file(path);
        checkpoint(file);
        FileUtil.mkParentDirs(file);
        FileUtil.writeUtf8String(content, file);
        return "Wrote file: " + file.getAbsolutePath();
    }

    @ToolMapping(name = "patch", description = "Replace one text snippet with another inside a UTF-8 text file.")
    public String patch(String path, String findText, String replaceText) throws Exception {
        File file = FileUtil.file(path);
        String original = FileUtil.readUtf8String(file);
        if (StrUtil.isEmpty(findText) || !original.contains(findText)) {
            return "Patch target not found in file: " + file.getAbsolutePath();
        }
        checkpoint(file);
        FileUtil.writeUtf8String(original.replace(findText, replaceText), file);
        return "Patched file: " + file.getAbsolutePath();
    }

    @ToolMapping(name = "search_files", description = "Search for text inside files under a directory path.")
    public String searchFiles(String rootPath, String pattern) {
        List<File> files = FileUtil.loopFiles(FileUtil.file(rootPath));
        StringBuilder buffer = new StringBuilder();
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }

            String text = FileUtil.readUtf8String(file);
            if (text.contains(pattern)) {
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(file.getAbsolutePath());
            }
        }

        return buffer.length() == 0 ? "No matches" : buffer.toString();
    }

    /**
     * 在结构化写操作前创建 checkpoint。
     */
    private void checkpoint(File file) throws Exception {
        if (checkpointService == null) {
            return;
        }
        SessionRecord session = sessionRepository == null ? null : sessionRepository.getBoundSession(sourceKey);
        checkpointService.createCheckpoint(
                sourceKey,
                session == null ? null : session.getSessionId(),
                Collections.singletonList(file)
        );
    }
}
