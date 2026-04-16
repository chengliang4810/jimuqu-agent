package com.jimuqu.agent.tool.runtime;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.DelegationService;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.constants.ToolNameConstants;
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
 * 默认工具注册表。
 */
public class DefaultToolRegistry implements ToolRegistry {
    /**
     * 默认内置工具清单。
     */
    private static final List<String> TOOL_NAMES = Arrays.asList(
            ToolNameConstants.TERMINAL,
            ToolNameConstants.PROCESS,
            ToolNameConstants.READ_FILE,
            ToolNameConstants.WRITE_FILE,
            ToolNameConstants.PATCH,
            ToolNameConstants.SEARCH_FILES,
            ToolNameConstants.EXECUTE_CODE,
            ToolNameConstants.DELEGATE_TASK,
            ToolNameConstants.TODO,
            ToolNameConstants.MEMORY,
            ToolNameConstants.SESSION_SEARCH,
            ToolNameConstants.SKILLS_LIST,
            ToolNameConstants.SKILL_VIEW,
            ToolNameConstants.SKILL_MANAGE,
            ToolNameConstants.SEND_MESSAGE,
            ToolNameConstants.CRONJOB,
            ToolNameConstants.APPROVAL,
            ToolNameConstants.CODESEARCH,
            ToolNameConstants.WEBSEARCH,
            ToolNameConstants.WEBFETCH
    );

    /**
     * 应用配置。
     */
    private final AppConfig appConfig;

    /**
     * 偏好存储。
     */
    private final SqlitePreferenceStore preferenceStore;

    /**
     * 会话仓储。
     */
    private final SessionRepository sessionRepository;

    /**
     * 定时任务仓储。
     */
    private final CronJobRepository cronJobRepository;

    /**
     * 渠道投递服务。
     */
    private final DeliveryService deliveryService;

    /**
     * 进程注册表。
     */
    private final ProcessRegistry processRegistry;

    /**
     * 长期记忆服务。
     */
    private final MemoryService memoryService;

    /**
     * 本地技能目录服务。
     */
    private final LocalSkillService localSkillService;

    /**
     * checkpoint 服务。
     */
    private final CheckpointService checkpointService;

    /**
     * 委托服务。
     */
    private final DelegationService delegationService;

    /**
     * 构造工具注册表。
     */
    public DefaultToolRegistry(AppConfig appConfig,
                               SqlitePreferenceStore preferenceStore,
                               SessionRepository sessionRepository,
                               CronJobRepository cronJobRepository,
                               DeliveryService deliveryService,
                               ProcessRegistry processRegistry,
                               MemoryService memoryService,
                               LocalSkillService localSkillService,
                               CheckpointService checkpointService,
                               DelegationService delegationService) {
        this.appConfig = appConfig;
        this.preferenceStore = preferenceStore;
        this.sessionRepository = sessionRepository;
        this.cronJobRepository = cronJobRepository;
        this.deliveryService = deliveryService;
        this.processRegistry = processRegistry;
        this.memoryService = memoryService;
        this.localSkillService = localSkillService;
        this.checkpointService = checkpointService;
        this.delegationService = delegationService;
    }

    @Override
    public List<String> listToolNames() {
        return TOOL_NAMES;
    }

    @Override
    public List<Object> resolveEnabledTools(String sourceKey) {
        List<Object> tools = new ArrayList<Object>();
        Set<Object> unique = new LinkedHashSet<Object>();

        FileTools fileTools = new FileTools(checkpointService, sessionRepository, sourceKey);
        ShellTools shellTools = new ShellTools(processRegistry);
        TodoTools todoTools = new TodoTools(appConfig, sourceKey);
        MemoryTools memoryTools = new MemoryTools(memoryService);
        SessionSearchTools sessionSearchTools = new SessionSearchTools(sessionRepository);
        SkillTools skillTools = new SkillTools(localSkillService, checkpointService, sessionRepository, sourceKey);
        MessagingTools messagingTools = new MessagingTools(deliveryService, sourceKey);
        CronjobTools cronjobTools = new CronjobTools(cronJobRepository, sourceKey);
        DelegateTools delegateTools = new DelegateTools(delegationService, sourceKey);
        WebsearchTool websearchTool = WebsearchTool.getInstance();
        WebfetchTool webfetchTool = WebfetchTool.getInstance();
        CodeSearchTool codeSearchTool = CodeSearchTool.getInstance();

        for (String toolName : TOOL_NAMES) {
            if (!isEnabled(sourceKey, toolName)) {
                continue;
            }

            if (ToolNameConstants.READ_FILE.equals(toolName)
                    || ToolNameConstants.WRITE_FILE.equals(toolName)
                    || ToolNameConstants.PATCH.equals(toolName)
                    || ToolNameConstants.SEARCH_FILES.equals(toolName)) {
                unique.add(fileTools);
            } else if (ToolNameConstants.TERMINAL.equals(toolName)
                    || ToolNameConstants.PROCESS.equals(toolName)
                    || ToolNameConstants.EXECUTE_CODE.equals(toolName)
                    || ToolNameConstants.APPROVAL.equals(toolName)) {
                unique.add(shellTools);
            } else if (ToolNameConstants.TODO.equals(toolName)) {
                unique.add(todoTools);
            } else if (ToolNameConstants.MEMORY.equals(toolName)) {
                unique.add(memoryTools);
            } else if (ToolNameConstants.SESSION_SEARCH.equals(toolName)) {
                unique.add(sessionSearchTools);
            } else if (ToolNameConstants.SKILLS_LIST.equals(toolName)
                    || ToolNameConstants.SKILL_VIEW.equals(toolName)
                    || ToolNameConstants.SKILL_MANAGE.equals(toolName)) {
                unique.add(skillTools);
            } else if (ToolNameConstants.SEND_MESSAGE.equals(toolName)) {
                unique.add(messagingTools);
            } else if (ToolNameConstants.CRONJOB.equals(toolName)) {
                unique.add(cronjobTools);
            } else if (ToolNameConstants.DELEGATE_TASK.equals(toolName)) {
                unique.add(delegateTools);
            } else if (ToolNameConstants.WEBSEARCH.equals(toolName)) {
                unique.add(websearchTool);
            } else if (ToolNameConstants.WEBFETCH.equals(toolName)) {
                unique.add(webfetchTool);
            } else if (ToolNameConstants.CODESEARCH.equals(toolName)) {
                unique.add(codeSearchTool);
            }
        }

        tools.addAll(unique);
        return tools;
    }

    @Override
    public void enableTools(String sourceKey, List<String> toolNames) {
        for (String toolName : toolNames) {
            if (TOOL_NAMES.contains(toolName)) {
                setToolEnabled(sourceKey, toolName, true);
            }
        }
    }

    @Override
    public void disableTools(String sourceKey, List<String> toolNames) {
        for (String toolName : toolNames) {
            if (TOOL_NAMES.contains(toolName)) {
                setToolEnabled(sourceKey, toolName, false);
            }
        }
    }

    /**
     * 读取工具启用状态。
     */
    private boolean isEnabled(String sourceKey, String toolName) {
        try {
            return preferenceStore.isToolEnabled(sourceKey, toolName);
        } catch (SQLException e) {
            return true;
        }
    }

    /**
     * 设置工具启用状态。
     */
    private void setToolEnabled(String sourceKey, String toolName, boolean enabled) {
        try {
            preferenceStore.setToolEnabled(sourceKey, toolName, enabled);
        } catch (SQLException ignored) {
            // V1 忽略偏好写入失败。
        }
    }
}
