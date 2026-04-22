package com.jimuqu.agent.context;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.support.constants.ContextFileConstants;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * runtime/ 根目录下人格工作区文件的统一访问服务。
 */
public class PersonaWorkspaceService {
    private static final String TEMPLATE_ROOT = "persona-templates/";

    private final File workspaceDir;

    public PersonaWorkspaceService(AppConfig appConfig) {
        this.workspaceDir = FileUtil.file(appConfig.getRuntime().getHome());
        FileUtil.mkdir(this.workspaceDir);
        ensureSeeded();
    }

    /**
     * 返回受控文件 key 顺序。
     */
    public List<String> orderedKeys() {
        return ContextFileConstants.orderedKeys();
    }

    /**
     * 解析 key 对应文件名。
     */
    public String fileName(String key) {
        return ContextFileConstants.fileName(key);
    }

    /**
     * 获取 key 对应文件。
     */
    public File file(String key) {
        return FileUtil.file(workspaceDir, fileName(key));
    }

    /**
     * 获取 key 对应文件绝对路径。
     */
    public String absolutePath(String key) {
        return file(key).getAbsolutePath();
    }

    /**
     * 判断文件是否存在。
     */
    public boolean exists(String key) {
        return file(key).exists();
    }

    /**
     * 读取文件内容；不存在时返回空字符串。
     */
    public String read(String key) {
        File target = file(key);
        if (!target.exists()) {
            return "";
        }
        return FileUtil.readUtf8String(target);
    }

    /**
     * 读取供系统提示词使用的正文内容。
     */
    public String readPromptBody(String key) {
        return read(key);
    }

    /**
     * 写入文件内容，不存在时自动创建。
     */
    public void write(String key, String content) {
        FileUtil.mkdir(workspaceDir);
        FileUtil.writeUtf8String(StrUtil.nullToEmpty(content), file(key));
    }

    /**
     * 读取 key 对应模板内容。
     */
    public String readTemplate(String key) {
        return loadTemplate(key);
    }

    /**
     * 将文件恢复为模板默认内容。
     */
    public void restoreTemplate(String key) {
        write(key, readTemplate(key));
    }

    /**
     * 启动时补齐缺失的人格工作区文件。
     */
    private void ensureSeeded() {
        for (String key : orderedKeys()) {
            File target = file(key);
            if (target.exists()) {
                continue;
            }
            FileUtil.writeUtf8String(loadTemplate(key), target);
        }
    }

    /**
     * 从类路径加载原始模板。
     */
    private String loadTemplate(String key) {
        String normalized = ContextFileConstants.normalizeKey(key);
        if (ContextFileConstants.KEY_MEMORY.equals(normalized)) {
            return "";
        }
        if (ContextFileConstants.KEY_MEMORY_TODAY.equals(ContextFileConstants.normalizeKey(key))) {
            return buildTodayMemoryTemplate(LocalDate.now());
        }
        String resource = TEMPLATE_ROOT + fileName(key);
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing persona template resource: " + resource);
        }
        try {
            return IoUtil.read(stream, "UTF-8");
        } finally {
            IoUtil.close(stream);
        }
    }

    private String buildTodayMemoryTemplate(LocalDate date) {
        return "# " + date.toString() + "\n\n";
    }
}
