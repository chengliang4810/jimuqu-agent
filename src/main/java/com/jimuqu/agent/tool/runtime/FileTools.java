package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.support.RuntimePathGuard;
import lombok.RequiredArgsConstructor;
import org.noear.solon.annotation.Param;
import org.noear.solon.ai.annotation.ToolMapping;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 文件工具。
 */
@RequiredArgsConstructor
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

    private final RuntimePathGuard pathGuard;

    public FileTools(CheckpointService checkpointService,
                     SessionRepository sessionRepository,
                     String sourceKey) {
        this(checkpointService, sessionRepository, sourceKey, defaultPathGuard());
    }

    private static RuntimePathGuard defaultPathGuard() {
        return new RuntimePathGuard(com.jimuqu.agent.config.AppConfig.load(new org.noear.solon.core.Props()));
    }

    @ToolMapping(name = "read_file", description = "Read a UTF-8 text file from disk by absolute or relative path.")
    public String readFile(@Param(name = "path", description = "文件绝对路径或相对路径") String path) {
        File file;
        try {
            file = pathGuard.requireAllowedToolPath(path);
        } catch (IllegalArgumentException e) {
            return "Cannot read file: " + e.getMessage();
        }
        if (path != null && (path.contains("!/") || path.contains("!\\"))) {
            return "Cannot read file: jar-internal paths are not disk files: " + path;
        }
        if (!file.exists()) {
            return "File not found: " + file.getAbsolutePath();
        }
        if (!file.isFile()) {
            return "Path is not a file: " + file.getAbsolutePath();
        }
        return FileUtil.readUtf8String(file);
    }

    @ToolMapping(name = "write_file", description = "Write UTF-8 text content to a file path, creating parent directories when needed.")
    public String writeFile(@Param(name = "path", description = "目标文件路径") String path,
                            @Param(name = "content", description = "写入的 UTF-8 文本内容") String content) throws Exception {
        File file = pathGuard.requireAllowedToolPath(path);
        checkpoint(file);
        FileUtil.mkParentDirs(file);
        FileUtil.writeUtf8String(content, file);
        return "Wrote file: " + file.getAbsolutePath();
    }

    @ToolMapping(name = "patch", description = "Replace one text snippet with another inside a UTF-8 text file.")
    public String patch(@Param(name = "path", description = "目标文件路径") String path,
                        @Param(name = "findText", description = "要替换的原始文本") String findText,
                        @Param(name = "replaceText", description = "替换后的文本") String replaceText) throws Exception {
        File file = pathGuard.requireAllowedToolPath(path);
        String original = FileUtil.readUtf8String(file);
        if (StrUtil.isEmpty(findText) || !original.contains(findText)) {
            return "Patch target not found in file: " + file.getAbsolutePath();
        }
        if (original.indexOf(findText) != original.lastIndexOf(findText)) {
            return "Patch target is not unique in file: " + file.getAbsolutePath();
        }
        checkpoint(file);
        FileUtil.writeUtf8String(original.replace(findText, replaceText), file);
        return "Patched file: " + file.getAbsolutePath();
    }

    @ToolMapping(name = "search_files", description = "Search for text inside files under the current project directory or configured runtime home.")
    public String searchFiles(@Param(name = "rootPath", description = "搜索根目录") String rootPath,
                              @Param(name = "pattern", description = "要搜索的文本模式") String pattern) {
        File root;
        try {
            root = pathGuard.requireAllowedToolPath(rootPath);
        } catch (IllegalArgumentException e) {
            return "Cannot search files: " + e.getMessage();
        }
        if (!root.exists()) {
            return "Search root not found: " + root.getAbsolutePath();
        }
        if (!root.isDirectory()) {
            return "Search root is not a directory: " + root.getAbsolutePath();
        }
        List<File> files = FileUtil.loopFiles(root);
        StringBuilder buffer = new StringBuilder();
        int scanned = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            if (++scanned > 2000 || file.length() > 1024L * 1024L) {
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

    /**
     * `read_file` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class ReadFileTool {
        private final FileTools delegate;

        @ToolMapping(name = "read_file", description = "Read a UTF-8 text file from disk by absolute or relative path.")
        public String readFile(@Param(name = "path", description = "文件绝对路径或相对路径") String path) {
            return delegate.readFile(path);
        }
    }

    /**
     * `write_file` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class WriteFileTool {
        private final FileTools delegate;

        @ToolMapping(name = "write_file", description = "Write UTF-8 text content to a file path, creating parent directories when needed.")
        public String writeFile(@Param(name = "path", description = "目标文件路径") String path,
                                @Param(name = "content", description = "写入的 UTF-8 文本内容") String content) throws Exception {
            return delegate.writeFile(path, content);
        }
    }

    /**
     * `patch` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class PatchTool {
        private final FileTools delegate;

        @ToolMapping(name = "patch", description = "Replace one text snippet with another inside a UTF-8 text file.")
        public String patch(@Param(name = "path", description = "目标文件路径") String path,
                            @Param(name = "findText", description = "要替换的原始文本") String findText,
                            @Param(name = "replaceText", description = "替换后的文本") String replaceText) throws Exception {
            return delegate.patch(path, findText, replaceText);
        }
    }

    /**
     * `search_files` 单工具暴露对象。
     */
    @RequiredArgsConstructor
    public static class SearchFilesTool {
        private final FileTools delegate;

        @ToolMapping(name = "search_files", description = "Search for text inside files under the current project directory or configured runtime home.")
        public String searchFiles(@Param(name = "rootPath", description = "搜索根目录") String rootPath,
                                  @Param(name = "pattern", description = "要搜索的文本模式") String pattern) {
            return delegate.searchFiles(rootPath, pattern);
        }
    }
}
