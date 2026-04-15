package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.noear.solon.ai.annotation.ToolMapping;

import java.io.File;
import java.util.List;

/**
 * FileTools 实现。
 */
public class FileTools {
    @ToolMapping(name = "read_file", description = "Read a UTF-8 text file from disk by absolute or relative path.")
    public String readFile(String path) {
        return FileUtil.readUtf8String(FileUtil.file(path));
    }

    @ToolMapping(name = "write_file", description = "Write UTF-8 text content to a file path, creating parent directories when needed.")
    public String writeFile(String path, String content) {
        File file = FileUtil.file(path);
        FileUtil.mkParentDirs(file);
        FileUtil.writeUtf8String(content, file);
        return "Wrote file: " + file.getAbsolutePath();
    }

    @ToolMapping(name = "patch", description = "Replace one text snippet with another inside a UTF-8 text file.")
    public String patch(String path, String findText, String replaceText) {
        File file = FileUtil.file(path);
        String original = FileUtil.readUtf8String(file);
        if (StrUtil.isEmpty(findText) || original.contains(findText) == false) {
            return "Patch target not found in file: " + file.getAbsolutePath();
        }
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
}
