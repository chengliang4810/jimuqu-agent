package com.jimuqu.agent.tool.runtime;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.service.ConversationOrchestrator;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.ConversationOrchestratorHolder;
import com.jimuqu.agent.tool.builtin.CodeSearchTool;
import com.jimuqu.agent.tool.builtin.WebfetchTool;
import com.jimuqu.agent.tool.builtin.WebsearchTool;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DefaultToolRegistry 实现。
 */
public class DefaultToolRegistry implements ToolRegistry {
    private static final List<String> TOOL_NAMES = Arrays.asList(
            "terminal",
            "process",
            "read_file",
            "write_file",
            "patch",
            "search_files",
            "execute_code",
            "delegate_task",
            "todo",
            "memory",
            "session_search",
            "send_message",
            "cronjob",
            "approval",
            "codesearch",
            "websearch",
            "webfetch"
    );

    private final AppConfig appConfig;
    private final SqlitePreferenceStore preferenceStore;
    private final SessionRepository sessionRepository;
    private final CronJobRepository cronJobRepository;
    private final DeliveryService deliveryService;
    private final ConversationOrchestratorHolder conversationHolder;
    private final ProcessRegistry processRegistry;

    public DefaultToolRegistry(AppConfig appConfig,
                               SqlitePreferenceStore preferenceStore,
                               SessionRepository sessionRepository,
                               CronJobRepository cronJobRepository,
                               DeliveryService deliveryService,
                               ConversationOrchestratorHolder conversationHolder,
                               ProcessRegistry processRegistry) {
        this.appConfig = appConfig;
        this.preferenceStore = preferenceStore;
        this.sessionRepository = sessionRepository;
        this.cronJobRepository = cronJobRepository;
        this.deliveryService = deliveryService;
        this.conversationHolder = conversationHolder;
        this.processRegistry = processRegistry;
    }

    public List<String> listToolNames() {
        return TOOL_NAMES;
    }

    public List<Object> resolveEnabledTools(String sourceKey) {
        List<Object> tools = new ArrayList<Object>();
        Set<Object> unique = new LinkedHashSet<Object>();

        FileTools fileTools = new FileTools();
        ShellTools shellTools = new ShellTools(processRegistry);
        TodoTools todoTools = new TodoTools(appConfig, sourceKey);
        MemoryTools memoryTools = new MemoryTools(appConfig);
        SessionSearchTools sessionSearchTools = new SessionSearchTools(sessionRepository);
        MessagingTools messagingTools = new MessagingTools(deliveryService, sourceKey);
        CronjobTools cronjobTools = new CronjobTools(cronJobRepository, sourceKey);
        DelegateTools delegateTools = new DelegateTools(conversationHolder.get(), sourceKey);
        WebsearchTool websearchTool = WebsearchTool.getInstance();
        WebfetchTool webfetchTool = WebfetchTool.getInstance();
        CodeSearchTool codeSearchTool = CodeSearchTool.getInstance();

        for (String toolName : TOOL_NAMES) {
            if (isEnabled(sourceKey, toolName) == false) {
                continue;
            }

            if ("read_file".equals(toolName) || "write_file".equals(toolName) || "patch".equals(toolName) || "search_files".equals(toolName)) {
                unique.add(fileTools);
            } else if ("terminal".equals(toolName) || "process".equals(toolName) || "execute_code".equals(toolName) || "approval".equals(toolName)) {
                unique.add(shellTools);
            } else if ("todo".equals(toolName)) {
                unique.add(todoTools);
            } else if ("memory".equals(toolName)) {
                unique.add(memoryTools);
            } else if ("session_search".equals(toolName)) {
                unique.add(sessionSearchTools);
            } else if ("send_message".equals(toolName)) {
                unique.add(messagingTools);
            } else if ("cronjob".equals(toolName)) {
                unique.add(cronjobTools);
            } else if ("delegate_task".equals(toolName)) {
                unique.add(delegateTools);
            } else if ("websearch".equals(toolName)) {
                unique.add(websearchTool);
            } else if ("webfetch".equals(toolName)) {
                unique.add(webfetchTool);
            } else if ("codesearch".equals(toolName)) {
                unique.add(codeSearchTool);
            }
        }

        tools.addAll(unique);
        return tools;
    }

    public void enableTools(String sourceKey, List<String> toolNames) {
        for (String toolName : toolNames) {
            if (TOOL_NAMES.contains(toolName)) {
                setToolEnabled(sourceKey, toolName, true);
            }
        }
    }

    public void disableTools(String sourceKey, List<String> toolNames) {
        for (String toolName : toolNames) {
            if (TOOL_NAMES.contains(toolName)) {
                setToolEnabled(sourceKey, toolName, false);
            }
        }
    }

    private boolean isEnabled(String sourceKey, String toolName) {
        try {
            return preferenceStore.isToolEnabled(sourceKey, toolName);
        } catch (SQLException e) {
            return true;
        }
    }

    private void setToolEnabled(String sourceKey, String toolName, boolean enabled) {
        try {
            preferenceStore.setToolEnabled(sourceKey, toolName, enabled);
        } catch (SQLException ignored) {
            // Ignore toggle persistence failures for V1.
        }
    }
}
