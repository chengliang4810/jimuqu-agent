package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.config.AppConfig;
import org.noear.solon.ai.annotation.ToolMapping;

import java.io.File;

/**
 * MemoryTools 实现。
 */
public class MemoryTools {
    private final AppConfig appConfig;

    public MemoryTools(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @ToolMapping(name = "memory", description = "Manage long-term memory text. action can be read, append, or clear.")
    public String memory(String action, String value) {
        File file = FileUtil.file(appConfig.getRuntime().getContextDir(), "MEMORY.md");
        if ("append".equalsIgnoreCase(action)) {
            FileUtil.appendUtf8String("- " + value + System.lineSeparator(), file);
            return "Memory appended";
        }
        if ("clear".equalsIgnoreCase(action)) {
            FileUtil.writeUtf8String("", file);
            return "Memory cleared";
        }
        if (file.exists() == false) {
            return "";
        }
        return FileUtil.readUtf8String(file);
    }
}
