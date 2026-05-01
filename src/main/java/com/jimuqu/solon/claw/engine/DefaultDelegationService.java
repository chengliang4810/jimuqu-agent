package com.jimuqu.solon.claw.engine;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.DelegationResult;
import com.jimuqu.solon.claw.core.model.DelegationTask;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import com.jimuqu.solon.claw.core.model.SubagentRunRecord;
import com.jimuqu.solon.claw.core.repository.AgentRunRepository;
import com.jimuqu.solon.claw.core.repository.SessionRepository;
import com.jimuqu.solon.claw.core.service.DelegationService;
import com.jimuqu.solon.claw.storage.repository.SqlitePreferenceStore;
import com.jimuqu.solon.claw.support.ConversationOrchestratorHolder;
import com.jimuqu.solon.claw.support.IdSupport;
import com.jimuqu.solon.claw.support.constants.ToolNameConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.noear.snack4.ONode;

/** 默认子代理委托服务。 */
public class DefaultDelegationService implements DelegationService {
    /** 委托日志器。 */
    private static final Logger log = LoggerFactory.getLogger(DefaultDelegationService.class);

    /** 默认最大并行数。 */
    private static final int MAX_CONCURRENT = 3;

    /** 子代理固定禁用的工具。 */
    private static final List<String> BLOCKED_TOOLS =
            Arrays.asList(
                    ToolNameConstants.DELEGATE_TASK,
                    ToolNameConstants.MEMORY,
                    ToolNameConstants.SEND_MESSAGE,
                    ToolNameConstants.CRONJOB,
                    ToolNameConstants.EXECUTE_PYTHON,
                    ToolNameConstants.EXECUTE_JS);

    /** 当前系统已知工具清单。 */
    private static final List<String> ALL_TOOLS =
            Arrays.asList(
                    ToolNameConstants.FILE_READ,
                    ToolNameConstants.FILE_WRITE,
                    ToolNameConstants.FILE_LIST,
                    ToolNameConstants.FILE_DELETE,
                    ToolNameConstants.EXECUTE_SHELL,
                    ToolNameConstants.EXECUTE_PYTHON,
                    ToolNameConstants.EXECUTE_JS,
                    ToolNameConstants.GET_CURRENT_TIME,
                    ToolNameConstants.TODO,
                    ToolNameConstants.DELEGATE_TASK,
                    ToolNameConstants.MEMORY,
                    ToolNameConstants.SESSION_SEARCH,
                    ToolNameConstants.SKILLS_LIST,
                    ToolNameConstants.SKILL_VIEW,
                    ToolNameConstants.SKILL_MANAGE,
                    ToolNameConstants.SEND_MESSAGE,
                    ToolNameConstants.CRONJOB,
                    ToolNameConstants.CODESEARCH,
                    ToolNameConstants.WEBSEARCH,
                    ToolNameConstants.WEBFETCH);

    /** 对话编排器。 */
    private final ConversationOrchestratorHolder conversationHolder;

    /** 工具注册表。 */
    private final SqlitePreferenceStore preferenceStore;

    /** 会话仓储。 */
    private final SessionRepository sessionRepository;

    /** Agent run 轨迹仓储。 */
    private final AgentRunRepository agentRunRepository;

    /** Hermes 风格暂停新子代理 spawn。 */
    private volatile boolean spawnPaused;

    public DefaultDelegationService(
            ConversationOrchestratorHolder conversationHolder,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository) {
        this(conversationHolder, preferenceStore, sessionRepository, null);
    }

    public DefaultDelegationService(
            ConversationOrchestratorHolder conversationHolder,
            SqlitePreferenceStore preferenceStore,
            SessionRepository sessionRepository,
            AgentRunRepository agentRunRepository) {
        this.conversationHolder = conversationHolder;
        this.preferenceStore = preferenceStore;
        this.sessionRepository = sessionRepository;
        this.agentRunRepository = agentRunRepository;
    }

    @Override
    public DelegationResult delegateSingle(String sourceKey, String prompt, String context)
            throws Exception {
        DelegationTask task = new DelegationTask();
        task.setName("delegate");
        task.setPrompt(prompt);
        task.setContext(context);
        return delegateSingle(sourceKey, task);
    }

    @Override
    public DelegationResult delegateSingle(String sourceKey, DelegationTask task) throws Exception {
        String prompt = task == null ? null : task.getPrompt();
        if (StrUtil.isBlank(prompt)) {
            return failureResult("delegate", "委托任务不能为空。");
        }
        if (spawnPaused) {
            return failureResult("delegate", "Subagent spawning is paused.");
        }

        try {
            SessionRecord parentSession = sessionRepository.getBoundSession(sourceKey);
            String subagentId = "sa-" + IdSupport.newId();
            String childSourceKey = sourceKey + ":delegate:" + IdSupport.newId();
            cloneToolVisibility(sourceKey, childSourceKey);
            applyAllowedTools(childSourceKey, task == null ? null : task.getAllowedTools());
            applyBlockedTools(childSourceKey);
            prepareChildSession(childSourceKey, parentSession);

            if (conversationHolder.get() == null) {
                return failureResult("delegate", "Conversation orchestrator is not ready");
            }
            GatewayMessage message =
                    new GatewayMessage(PlatformType.MEMORY, "", "", decoratePrompt(task));
            message.setSourceKeyOverride(childSourceKey);
            SubagentRunRecord subagent = startSubagent(subagentId, sourceKey, childSourceKey, task);
            GatewayReply reply = conversationHolder.get().handleIncoming(message);
            finishSubagent(subagent, reply);

            DelegationResult result = new DelegationResult();
            result.setSubagentId(subagentId);
            result.setName(
                    StrUtil.blankToDefault(task == null ? null : task.getName(), "delegate"));
            result.setSessionId(reply == null ? null : reply.getSessionId());
            result.setSourceKey(childSourceKey);
            result.setContent(reply == null ? "" : reply.getContent());
            result.setError(reply != null && reply.isError());
            return result;
        } catch (Exception e) {
            log.warn("delegateSingle failed: sourceKey={}, prompt={}", sourceKey, prompt, e);
            return failureResult("delegate", e.getMessage());
        }
    }

    public void setSpawnPaused(boolean paused) {
        this.spawnPaused = paused;
    }

    public boolean isSpawnPaused() {
        return spawnPaused;
    }

    @Override
    public List<DelegationResult> delegateBatch(final String sourceKey, List<DelegationTask> tasks)
            throws Exception {
        List<DelegationResult> results = new ArrayList<DelegationResult>();
        if (tasks == null || tasks.isEmpty()) {
            return results;
        }

        ExecutorService executorService =
                Executors.newFixedThreadPool(Math.min(MAX_CONCURRENT, tasks.size()));
        try {
            List<Future<DelegationResult>> futures = new ArrayList<Future<DelegationResult>>();
            for (final DelegationTask task : tasks) {
                futures.add(
                        executorService.submit(
                                new Callable<DelegationResult>() {
                                    @Override
                                    public DelegationResult call() throws Exception {
                                        DelegationResult result = delegateSingle(sourceKey, task);
                                        result.setName(
                                                StrUtil.blankToDefault(task.getName(), "delegate"));
                                        return result;
                                    }
                                }));
            }

            for (Future<DelegationResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    log.warn("delegateBatch child failed: sourceKey={}", sourceKey, e);
                    results.add(failureResult("delegate", e.getMessage()));
                }
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    /** 复制父来源键的工具可见性。 */
    private void cloneToolVisibility(String parentSourceKey, String childSourceKey)
            throws Exception {
        for (String toolName : ALL_TOOLS) {
            boolean enabled = preferenceStore.isToolEnabled(parentSourceKey, toolName);
            preferenceStore.setToolEnabled(childSourceKey, toolName, enabled);
        }
    }

    /** 对子会话应用固定黑名单。 */
    private void applyBlockedTools(String childSourceKey) throws Exception {
        for (String blockedTool : BLOCKED_TOOLS) {
            preferenceStore.setToolEnabled(childSourceKey, blockedTool, false);
        }
    }

    private void applyAllowedTools(String childSourceKey, List<String> allowedTools)
            throws Exception {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return;
        }
        for (String toolName : ALL_TOOLS) {
            preferenceStore.setToolEnabled(childSourceKey, toolName, false);
        }
        for (String toolName : allowedTools) {
            if (toolName != null && ALL_TOOLS.contains(toolName.trim())) {
                preferenceStore.setToolEnabled(childSourceKey, toolName.trim(), true);
            }
        }
    }

    /** 预先创建子会话并写入父会话关系。 */
    private void prepareChildSession(String childSourceKey, SessionRecord parentSession)
            throws Exception {
        SessionRecord existing = sessionRepository.getBoundSession(childSourceKey);
        if (existing != null) {
            return;
        }

        SessionRecord childSession = sessionRepository.bindNewSession(childSourceKey);
        if (parentSession != null) {
            childSession.setParentSessionId(parentSession.getSessionId());
        }
        sessionRepository.save(childSession);
    }

    /** 拼接委托上下文。 */
    private String decoratePrompt(DelegationTask task) {
        String prompt = task == null ? "" : task.getPrompt();
        String context = task == null ? "" : task.getContext();
        StringBuilder buffer = new StringBuilder();
        buffer.append("任务目标:\n").append(prompt);
        if (StrUtil.isBlank(context)) {
            context = "";
        }
        if (StrUtil.isNotBlank(context)) {
            buffer.append("\n\n补充上下文:\n").append(context);
        }
        if (task != null && StrUtil.isNotBlank(task.getExpectedOutput())) {
            buffer.append("\n\n期望输出:\n").append(task.getExpectedOutput());
        }
        if (task != null && StrUtil.isNotBlank(task.getWriteScope())) {
            buffer.append("\n\n写入范围:\n").append(task.getWriteScope());
        }
        return buffer.toString();
    }

    /** 构造失败结果，避免单个子任务异常打断整个批次。 */
    private DelegationResult failureResult(String name, String message) {
        DelegationResult result = new DelegationResult();
        result.setName(StrUtil.blankToDefault(name, "delegate"));
        result.setError(true);
        result.setContent(StrUtil.blankToDefault(message, "delegation failed"));
        return result;
    }

    private SubagentRunRecord startSubagent(
            String subagentId, String parentSourceKey, String childSourceKey, DelegationTask task) {
        SubagentRunRecord record = new SubagentRunRecord();
        long now = System.currentTimeMillis();
        record.setSubagentId(subagentId);
        record.setParentSourceKey(parentSourceKey);
        record.setChildSourceKey(childSourceKey);
        record.setName(StrUtil.blankToDefault(task == null ? null : task.getName(), "delegate"));
        record.setGoalPreview(
                com.jimuqu.solon.claw.core.model.AgentRunContext.safe(
                        task == null ? null : task.getPrompt(), 1000));
        record.setStatus("running");
        record.setDepth(1);
        record.setStartedAt(now);
        record.setHeartbeatAt(now);
        saveSubagent(record);
        return record;
    }

    private void finishSubagent(SubagentRunRecord record, GatewayReply reply) {
        if (record == null) {
            return;
        }
        record.setStatus(reply != null && reply.isError() ? "failed" : "success");
        record.setSessionId(reply == null ? null : reply.getSessionId());
        record.setError(reply != null && reply.isError() ? reply.getContent() : null);
        record.setOutputTailJson(buildTailJson(reply == null ? "" : reply.getContent()));
        record.setFinishedAt(System.currentTimeMillis());
        record.setHeartbeatAt(record.getFinishedAt());
        saveSubagent(record);
    }

    private void saveSubagent(SubagentRunRecord record) {
        if (agentRunRepository == null) {
            return;
        }
        try {
            agentRunRepository.saveSubagentRun(record);
        } catch (Exception ignored) {
        }
    }

    private String buildTailJson(String content) {
        ONode array = new ONode().asArray();
        ONode item = new ONode().asObject();
        item.set(
                "preview",
                com.jimuqu.solon.claw.core.model.AgentRunContext.safe(content, 1000));
        item.set("is_error", false);
        array.add(item);
        return array.toJson();
    }
}
