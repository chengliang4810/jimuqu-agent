package com.jimuqu.agent.tool.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.support.constants.MemoryConstants;
import org.noear.solon.ai.annotation.ToolMapping;

/**
 * 长期记忆工具。
 */
public class MemoryTools {
    /**
     * 长期记忆服务。
     */
    private final MemoryService memoryService;

    /**
     * 构造记忆工具。
     */
    public MemoryTools(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 管理 MEMORY.md 与 USER.md。
     */
    @ToolMapping(name = "memory", description = "Manage persistent memory. action supports add, replace, remove, read. target supports memory or user.")
    public String memory(String action, String target, String content, String oldText) throws Exception {
        String normalizedTarget = StrUtil.blankToDefault(target, MemoryConstants.TARGET_MEMORY);
        if (MemoryConstants.ACTION_READ.equalsIgnoreCase(action)) {
            return memoryService.read(normalizedTarget);
        }
        if (MemoryConstants.ACTION_ADD.equalsIgnoreCase(action)) {
            return memoryService.add(normalizedTarget, content);
        }
        if (MemoryConstants.ACTION_REPLACE.equalsIgnoreCase(action)) {
            return memoryService.replace(normalizedTarget, oldText, content);
        }
        if (MemoryConstants.ACTION_REMOVE.equalsIgnoreCase(action)) {
            return memoryService.remove(normalizedTarget, StrUtil.blankToDefault(oldText, content));
        }
        return "Unsupported memory action";
    }
}
