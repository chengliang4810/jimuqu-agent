package com.jimuqu.agent.tool.runtime;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.context.LocalSkillService;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.core.service.DelegationService;
import com.jimuqu.agent.core.service.DeliveryService;
import com.jimuqu.agent.core.service.MemoryService;
import com.jimuqu.agent.core.service.SessionSearchService;
import com.jimuqu.agent.core.service.SkillHubService;
import com.jimuqu.agent.core.service.ToolRegistry;
import com.jimuqu.agent.support.AttachmentCacheService;
import com.jimuqu.agent.support.RuntimePathGuard;
import com.jimuqu.agent.support.RuntimeSettingsService;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.support.constants.ToolNameConstants;
import lombok.RequiredArgsConstructor;
import org.noear.solon.ai.skills.sys.NodejsSkill;
import org.noear.solon.ai.skills.sys.PythonSkill;
import org.noear.solon.ai.skills.sys.ShellSkill;
import org.noear.solon.ai.skills.sys.SystemClockSkill;
import org.noear.solon.ai.skills.web.CodeSearchTool;
import org.noear.solon.ai.skills.web.WebfetchTool;
import org.noear.solon.ai.skills.web.WebsearchTool;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 默认工具注册表。
 */
@RequiredArgsConstructor
public class DefaultToolRegistry implements ToolRegistry {
    /**
     * 默认内置工具清单。
     */
    private static final List<String> TOOL_NAMES = Arrays.asList(
            ToolNameConstants.READ_FILE,
            ToolNameConstants.WRITE_FILE,
            ToolNameConstants.PATCH,
            ToolNameConstants.SEARCH_FILES,
            ToolNameConstants.EXISTS_CMD,
            ToolNameConstants.LIST_FILES,
            ToolNameConstants.EXECUTE_SHELL,
            ToolNameConstants.EXECUTE_PYTHON,
            ToolNameConstants.EXECUTE_JS,
            ToolNameConstants.GET_CURRENT_TIME,
            ToolNameConstants.DELEGATE_TASK,
            ToolNameConstants.TODO,
            ToolNameConstants.MEMORY,
            ToolNameConstants.SESSION_SEARCH,
            ToolNameConstants.SKILLS_LIST,
            ToolNameConstants.SKILL_VIEW,
            ToolNameConstants.SKILL_MANAGE,
            ToolNameConstants.SKILLS_HUB_SEARCH,
            ToolNameConstants.SKILLS_HUB_INSPECT,
            ToolNameConstants.SKILLS_HUB_INSTALL,
            ToolNameConstants.SKILLS_HUB_LIST,
            ToolNameConstants.SKILLS_HUB_CHECK,
            ToolNameConstants.SKILLS_HUB_UPDATE,
            ToolNameConstants.SKILLS_HUB_AUDIT,
            ToolNameConstants.SKILLS_HUB_UNINSTALL,
            ToolNameConstants.SKILLS_HUB_TAP,
            ToolNameConstants.SEND_MESSAGE,
            ToolNameConstants.CRONJOB,
            ToolNameConstants.CONFIG_GET,
            ToolNameConstants.CONFIG_SET,
            ToolNameConstants.CONFIG_SET_SECRET,
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
     * 长期记忆服务。
     */
    private final MemoryService memoryService;

    /**
     * 会话搜索服务。
     */
    private final SessionSearchService sessionSearchService;

    /**
     * 本地技能目录服务。
     */
    private final LocalSkillService localSkillService;

    /**
     * Skills Hub 服务。
     */
    private final SkillHubService skillHubService;

    /**
     * checkpoint 服务。
     */
    private final CheckpointService checkpointService;

    /**
     * 委托服务。
     */
    private final DelegationService delegationService;

    /**
     * 附件缓存服务。
     */
    private final AttachmentCacheService attachmentCacheService;

    /**
     * 运行时配置服务。
     */
    private final RuntimeSettingsService runtimeSettingsService;

    private final RuntimePathGuard pathGuard;

    public DefaultToolRegistry(AppConfig appConfig,
                               SqlitePreferenceStore preferenceStore,
                               SessionRepository sessionRepository,
                               CronJobRepository cronJobRepository,
                               DeliveryService deliveryService,
                               MemoryService memoryService,
                               SessionSearchService sessionSearchService,
                               LocalSkillService localSkillService,
                               SkillHubService skillHubService,
                               CheckpointService checkpointService,
                               DelegationService delegationService,
                               AttachmentCacheService attachmentCacheService,
                               RuntimeSettingsService runtimeSettingsService) {
        this(appConfig, preferenceStore, sessionRepository, cronJobRepository, deliveryService, memoryService,
                sessionSearchService, localSkillService, skillHubService, checkpointService, delegationService,
                attachmentCacheService, runtimeSettingsService, new RuntimePathGuard(appConfig));
    }

    @Override
    public List<String> listToolNames() {
        return new ArrayList<String>(TOOL_NAMES);
    }

    @Override
    public List<Object> resolveEnabledTools(String sourceKey) {
        List<Object> tools = new ArrayList<Object>();

        FileTools fileTools = new FileTools(checkpointService, sessionRepository, sourceKey, pathGuard);
        TodoTools todoTools = new TodoTools(appConfig, sourceKey);
        MemoryTools memoryTools = new MemoryTools(memoryService);
        SessionSearchTools sessionSearchTools = new SessionSearchTools(sessionSearchService, sourceKey);
        SkillTools skillTools = new SkillTools(localSkillService, checkpointService, sessionRepository, sourceKey);
        SkillHubTools skillHubTools = new SkillHubTools(skillHubService);
        MessagingTools messagingTools = new MessagingTools(deliveryService, sourceKey, attachmentCacheService, appConfig);
        CronjobTools cronjobTools = new CronjobTools(cronJobRepository, sourceKey);
        DelegateTools delegateTools = new DelegateTools(delegationService, sourceKey);
        ConfigTools configTools = new ConfigTools(runtimeSettingsService);
        String sysWorkDir = appConfig.getRuntime().getHome();
        ShellSkill shellSkill = new ShellSkill(sysWorkDir, defaultShellCommand(), defaultShellExtension());
        PythonSkill pythonSkill = new PythonSkill(sysWorkDir, defaultPythonCommand());
        NodejsSkill nodejsSkill = new NodejsSkill(sysWorkDir);
        SystemClockSkill systemClockSkill = new SystemClockSkill();
        WebsearchTool websearchTool = WebsearchTool.getInstance();
        WebfetchTool webfetchTool = WebfetchTool.getInstance();
        CodeSearchTool codeSearchTool = CodeSearchTool.getInstance();
        boolean clockSkillAdded = false;

        for (String toolName : TOOL_NAMES) {
            if (!isEnabled(sourceKey, toolName)) {
                continue;
            }

            if (ToolNameConstants.READ_FILE.equals(toolName)) {
                tools.add(new FileTools.ReadFileTool(fileTools));
            } else if (ToolNameConstants.WRITE_FILE.equals(toolName)) {
                tools.add(new FileTools.WriteFileTool(fileTools));
            } else if (ToolNameConstants.PATCH.equals(toolName)) {
                tools.add(new FileTools.PatchTool(fileTools));
            } else if (ToolNameConstants.SEARCH_FILES.equals(toolName)) {
                tools.add(new FileTools.SearchFilesTool(fileTools));
            } else if (ToolNameConstants.EXISTS_CMD.equals(toolName)) {
                tools.add(new SystemSkillTools.ExistsCmdTool(shellSkill));
            } else if (ToolNameConstants.LIST_FILES.equals(toolName)) {
                tools.add(new SystemSkillTools.ListFilesTool(shellSkill));
            } else if (ToolNameConstants.EXECUTE_SHELL.equals(toolName)) {
                tools.add(new SystemSkillTools.ExecuteShellTool(shellSkill));
            } else if (ToolNameConstants.EXECUTE_PYTHON.equals(toolName)) {
                tools.add(new SystemSkillTools.ExecutePythonTool(pythonSkill));
            } else if (ToolNameConstants.EXECUTE_JS.equals(toolName)) {
                tools.add(new SystemSkillTools.ExecuteJsTool(nodejsSkill));
            } else if (ToolNameConstants.GET_CURRENT_TIME.equals(toolName)) {
                if (!clockSkillAdded) {
                    tools.add(systemClockSkill);
                    clockSkillAdded = true;
                }
            } else if (ToolNameConstants.CONFIG_GET.equals(toolName)) {
                tools.add(new ConfigTools.ConfigGetTool(configTools));
            } else if (ToolNameConstants.CONFIG_SET.equals(toolName)) {
                tools.add(new ConfigTools.ConfigSetTool(configTools));
            } else if (ToolNameConstants.CONFIG_SET_SECRET.equals(toolName)) {
                tools.add(new ConfigTools.ConfigSetSecretTool(configTools));
            } else if (ToolNameConstants.TODO.equals(toolName)) {
                tools.add(todoTools);
            } else if (ToolNameConstants.MEMORY.equals(toolName)) {
                tools.add(memoryTools);
            } else if (ToolNameConstants.SESSION_SEARCH.equals(toolName)) {
                tools.add(sessionSearchTools);
            } else if (ToolNameConstants.SKILLS_LIST.equals(toolName)) {
                tools.add(new SkillTools.SkillsListTool(skillTools));
            } else if (ToolNameConstants.SKILL_VIEW.equals(toolName)) {
                tools.add(new SkillTools.SkillViewTool(skillTools));
            } else if (ToolNameConstants.SKILL_MANAGE.equals(toolName)) {
                tools.add(new SkillTools.SkillManageTool(skillTools));
            } else if (ToolNameConstants.SKILLS_HUB_SEARCH.equals(toolName)) {
                tools.add(new SkillHubTools.SearchTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_INSPECT.equals(toolName)) {
                tools.add(new SkillHubTools.InspectTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_INSTALL.equals(toolName)) {
                tools.add(new SkillHubTools.InstallTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_LIST.equals(toolName)) {
                tools.add(new SkillHubTools.ListTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_CHECK.equals(toolName)) {
                tools.add(new SkillHubTools.CheckTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_UPDATE.equals(toolName)) {
                tools.add(new SkillHubTools.UpdateTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_AUDIT.equals(toolName)) {
                tools.add(new SkillHubTools.AuditTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_UNINSTALL.equals(toolName)) {
                tools.add(new SkillHubTools.UninstallTool(skillHubTools));
            } else if (ToolNameConstants.SKILLS_HUB_TAP.equals(toolName)) {
                tools.add(new SkillHubTools.TapTool(skillHubTools));
            } else if (ToolNameConstants.SEND_MESSAGE.equals(toolName)) {
                tools.add(messagingTools);
            } else if (ToolNameConstants.CRONJOB.equals(toolName)) {
                tools.add(cronjobTools);
            } else if (ToolNameConstants.DELEGATE_TASK.equals(toolName)) {
                tools.add(delegateTools);
            } else if (ToolNameConstants.WEBSEARCH.equals(toolName)) {
                tools.add(websearchTool);
            } else if (ToolNameConstants.WEBFETCH.equals(toolName)) {
                tools.add(webfetchTool);
            } else if (ToolNameConstants.CODESEARCH.equals(toolName)) {
                tools.add(codeSearchTool);
            }
        }
        return tools;
    }

    @Override
    public List<String> resolveEnabledToolNames(String sourceKey) {
        List<String> result = new ArrayList<String>();
        for (String toolName : TOOL_NAMES) {
            if (isEnabled(sourceKey, toolName)) {
                result.add(toolName);
            }
        }
        return result;
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
            return false;
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

    private String defaultShellCommand() {
        return isWindows() ? "cmd /c" : "/bin/sh";
    }

    private String defaultShellExtension() {
        return isWindows() ? ".bat" : ".sh";
    }

    private String defaultPythonCommand() {
        return isWindows() ? "python" : "python3";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
