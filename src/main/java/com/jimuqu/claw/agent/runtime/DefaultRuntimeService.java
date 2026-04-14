package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.runtime.model.ChildRunRecord;
import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.agent.runtime.model.SessionRecord;
import com.jimuqu.claw.agent.runtime.model.ToolCallRecord;
import com.jimuqu.claw.agent.store.DedupStore;
import com.jimuqu.claw.agent.store.RouteStore;
import com.jimuqu.claw.agent.store.RunStore;
import com.jimuqu.claw.agent.store.SessionStore;
import com.jimuqu.claw.agent.tool.ToolRegistry;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.agent.workspace.WorkspacePromptService;
import com.jimuqu.claw.channel.ChannelAdapter;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;
import com.jimuqu.claw.channel.model.ChannelOutboundMessage;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.provider.ModelConfigResolver;
import com.jimuqu.claw.provider.model.ResolvedModelConfig;
import com.jimuqu.claw.support.Ids;
import org.noear.solon.ai.agent.react.ReActAgent;
import org.noear.solon.ai.agent.react.ReActResponse;
import org.noear.solon.ai.agent.session.FileAgentSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultRuntimeService implements RuntimeService {
    private static final Logger log = LoggerFactory.getLogger(DefaultRuntimeService.class);

    private final WorkspaceLayout workspaceLayout;
    private final ClawProperties properties;
    private final SessionStore sessionStore;
    private final RunStore runStore;
    private final RouteStore routeStore;
    private final DedupStore dedupStore;
    private final WorkspacePromptService workspacePromptService;
    private final ModelConfigResolver modelConfigResolver;
    private final ToolRegistry toolRegistry;
    private final List<ChannelAdapter> channelAdapters;
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<String, ReentrantLock>();

    public DefaultRuntimeService(
            WorkspaceLayout workspaceLayout,
            ClawProperties properties,
            SessionStore sessionStore,
            RunStore runStore,
            RouteStore routeStore,
            DedupStore dedupStore,
            WorkspacePromptService workspacePromptService,
            ModelConfigResolver modelConfigResolver,
            ToolRegistry toolRegistry,
            List<ChannelAdapter> channelAdapters) {
        this.workspaceLayout = workspaceLayout;
        this.properties = properties;
        this.sessionStore = sessionStore;
        this.runStore = runStore;
        this.routeStore = routeStore;
        this.dedupStore = dedupStore;
        this.workspacePromptService = workspacePromptService;
        this.modelConfigResolver = modelConfigResolver;
        this.toolRegistry = toolRegistry;
        this.channelAdapters = channelAdapters == null ? new ArrayList<ChannelAdapter>() : channelAdapters;
    }

    @Override
    public RunRecord handleInbound(ChannelInboundMessage inboundMessage) {
        RunRequest request = buildInboundRequest(inboundMessage);
        String dedupKey = buildDedupKey(inboundMessage, request.getSessionContext());
        if (dedupKey != null && !dedupStore.markIfAbsent(dedupKey)) {
            RunRecord skipped = buildBaseRunRecord(request, RunStatus.SKIPPED);
            skipped.setErrorMessage("Duplicate inbound message ignored.");
            skipped.setCompletedAt(Instant.now());
            runStore.save(skipped);
            return skipped;
        }

        return handleRequest(request);
    }

    @Override
    public RunRecord handleRequest(RunRequest request) {
        RunRequest normalizedRequest = normalizeRunRequest(request);
        ReentrantLock lock = sessionLocks.computeIfAbsent(normalizedRequest.getSessionContext().getSessionId(), key -> new ReentrantLock());
        lock.lock();
        try {
            return executeRequest(normalizedRequest);
        } finally {
            lock.unlock();
        }
    }

    protected AgentExecution executeAgent(
            RunRequest request,
            RunRecord runRecord,
            ResolvedModelConfig modelConfig,
            String systemPrompt) throws Throwable {
        FileAgentSession agentSession = new FileAgentSession(request.getSessionContext().getSessionId(), workspaceLayout.sessionAgentDir().toString());
        RunTracingInterceptor tracingInterceptor = new RunTracingInterceptor();
        ReActAgent agent = ReActAgent.of(modelConfigResolver.buildChatModel(modelConfig.getModelAlias()))
                .name("jimuqu_claw")
                .instruction(systemPrompt)
                .sessionWindowSize(properties.getRuntime().getSessionWindowSize())
                .maxSteps(properties.getRuntime().getMaxSteps())
                .defaultToolAdd(toolRegistry.allTools())
                .defaultInterceptorAdd(0, tracingInterceptor)
                .build();

        ReActResponse response = agent.prompt(StrUtil.nullToDefault(request.getUserMessage(), ""))
                .session(agentSession)
                .options(options -> options.toolContextPut(buildToolContext(request, runRecord)))
                .call();

        return new AgentExecution(
                response.getContent(),
                RunTracingInterceptor.extract(response.getTrace(), runRecord.getRunId()));
    }

    private RunRecord executeRequest(RunRequest request) {
        SessionContext sessionContext = request.getSessionContext();
        SessionRecord sessionRecord = upsertSessionRecord(sessionContext);
        if (request.getReplyRoute() != null) {
            routeStore.save(sessionContext.getSessionId(), request.getReplyRoute());
        }

        RunRecord runRecord = buildBaseRunRecord(request, RunStatus.PENDING);
        runStore.save(runRecord);

        try {
            ResolvedModelConfig modelConfig = modelConfigResolver.resolve(request.getModelAlias());
            String systemPrompt = resolveSystemPrompt(request, sessionContext);

            runRecord.setModelAlias(modelConfig.getModelAlias());
            runRecord.setStatus(RunStatus.RUNNING);
            runRecord.setStartedAt(Instant.now());
            runStore.save(runRecord);

            AgentExecution execution = executeAgent(request, runRecord, modelConfig, systemPrompt);

            runRecord.setStatus(RunStatus.SUCCEEDED);
            runRecord.setResponseText(execution.getResponseText());
            runRecord.setToolCalls(execution.getToolCalls());
            runRecord.setCompletedAt(Instant.now());
            runStore.save(runRecord);

            sessionRecord.setUpdatedAt(runRecord.getCompletedAt());
            sessionStore.save(sessionRecord);
            appendChildRun(runRecord);
            dispatchReply(request.getReplyRoute(), runRecord.getResponseText());
            return runRecord;
        } catch (Throwable e) {
            log.error("Runtime request failed: {}", runRecord.getRunId(), e);
            runRecord.setStatus(RunStatus.FAILED);
            runRecord.setErrorMessage(e.getMessage());
            runRecord.setCompletedAt(Instant.now());
            runStore.save(runRecord);

            sessionRecord.setUpdatedAt(runRecord.getCompletedAt());
            sessionStore.save(sessionRecord);
            appendChildRun(runRecord);
            return runRecord;
        }
    }

    private RunRequest buildInboundRequest(ChannelInboundMessage inboundMessage) {
        if (inboundMessage == null) {
            throw new IllegalArgumentException("Inbound message is required");
        }

        SessionContext sessionContext = normalizeSessionContext(copySessionContext(inboundMessage.getSessionContext()), inboundMessage.getMessageId());
        ReplyRoute replyRoute = resolveReplyRoute(inboundMessage, sessionContext.getSessionId());
        return RunRequest.builder()
                .sessionContext(sessionContext)
                .replyRoute(replyRoute)
                .userMessage(StrUtil.nullToDefault(inboundMessage.getText(), ""))
                .source("channel:" + StrUtil.nullToDefault(sessionContext.getPlatform(), "unknown"))
                .createdAt(inboundMessage.getReceivedAt() == null ? Instant.now() : inboundMessage.getReceivedAt())
                .build();
    }

    private RunRequest normalizeRunRequest(RunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Run request is required");
        }

        SessionContext sessionContext = normalizeSessionContext(copySessionContext(request.getSessionContext()), null);
        return RunRequest.builder()
                .runId(request.getRunId())
                .parentRunId(request.getParentRunId())
                .sessionContext(sessionContext)
                .replyRoute(request.getReplyRoute())
                .userMessage(StrUtil.nullToDefault(request.getUserMessage(), ""))
                .systemPrompt(request.getSystemPrompt())
                .modelAlias(request.getModelAlias())
                .source(StrUtil.blankToDefault(request.getSource(), "runtime"))
                .skillNames(request.getSkillNames() == null ? new ArrayList<String>() : new ArrayList<String>(request.getSkillNames()))
                .createdAt(request.getCreatedAt() == null ? Instant.now() : request.getCreatedAt())
                .build();
    }

    private SessionContext normalizeSessionContext(SessionContext context, String fallbackMessageId) {
        if (context == null) {
            throw new IllegalArgumentException("Run request missing session context");
        }

        if (StrUtil.isBlank(context.getSessionId())) {
            context.setSessionId(Ids.sessionId(context.getPlatform(), context.getChatId(), context.getThreadId(), context.getUserId()));
        }
        if (StrUtil.isBlank(context.getWorkspaceRoot())) {
            context.setWorkspaceRoot(workspaceLayout.getRoot().toString());
        }
        if (StrUtil.isBlank(context.getMessageId()) && StrUtil.isNotBlank(fallbackMessageId)) {
            context.setMessageId(fallbackMessageId);
        }
        if (context.getMetadata() == null) {
            context.setMetadata(new LinkedHashMap<String, Object>());
        }

        return context;
    }

    private SessionRecord upsertSessionRecord(SessionContext sessionContext) {
        SessionRecord current = sessionStore.get(sessionContext.getSessionId());
        Instant now = Instant.now();
        if (current == null) {
            current = SessionRecord.builder()
                    .sessionId(sessionContext.getSessionId())
                    .platform(sessionContext.getPlatform())
                    .chatId(sessionContext.getChatId())
                    .threadId(sessionContext.getThreadId())
                    .userId(sessionContext.getUserId())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            current.setPlatform(sessionContext.getPlatform());
            current.setChatId(sessionContext.getChatId());
            current.setThreadId(sessionContext.getThreadId());
            current.setUserId(sessionContext.getUserId());
            current.setUpdatedAt(now);
        }

        sessionStore.save(current);
        return current;
    }

    private ReplyRoute resolveReplyRoute(ChannelInboundMessage inboundMessage, String sessionId) {
        if (inboundMessage.getReplyRoute() != null) {
            return inboundMessage.getReplyRoute();
        }

        return routeStore.get(sessionId);
    }

    private RunRecord buildBaseRunRecord(RunRequest request, RunStatus status) {
        SessionContext sessionContext = request.getSessionContext();
        Instant createdAt = request.getCreatedAt() == null ? Instant.now() : request.getCreatedAt();
        return RunRecord.builder()
                .runId(StrUtil.blankToDefault(request.getRunId(), Ids.runId()))
                .parentRunId(request.getParentRunId())
                .sessionId(sessionContext.getSessionId())
                .status(status)
                .source(StrUtil.blankToDefault(request.getSource(), "runtime"))
                .userMessage(StrUtil.nullToDefault(request.getUserMessage(), ""))
                .requestDigest(buildRequestDigest(request))
                .createdAt(createdAt)
                .build();
    }

    private String buildDedupKey(ChannelInboundMessage inboundMessage, SessionContext sessionContext) {
        if (StrUtil.isBlank(inboundMessage.getMessageId())) {
            return null;
        }

        return StrUtil.nullToDefault(sessionContext.getPlatform(), "unknown") + "|" + inboundMessage.getMessageId();
    }

    private String resolveSystemPrompt(RunRequest request, SessionContext sessionContext) {
        if (StrUtil.isNotBlank(request.getSystemPrompt())) {
            return request.getSystemPrompt();
        }

        return workspacePromptService.buildSystemPrompt(sessionContext);
    }

    private String buildRequestDigest(RunRequest request) {
        SessionContext sessionContext = request.getSessionContext();
        String seed = StrUtil.blankToDefault(
                sessionContext.getMessageId(),
                sessionContext.getSessionId()
                        + "|"
                        + StrUtil.nullToDefault(request.getParentRunId(), "-")
                        + "|"
                        + StrUtil.nullToDefault(request.getUserMessage(), "")
                        + "|"
                        + request.getCreatedAt());
        return Ids.hashKey(seed);
    }

    private SessionContext copySessionContext(SessionContext sessionContext) {
        if (sessionContext == null) {
            return null;
        }

        Map<String, Object> metadata = sessionContext.getMetadata() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(sessionContext.getMetadata());

        return SessionContext.builder()
                .sessionId(sessionContext.getSessionId())
                .platform(sessionContext.getPlatform())
                .chatId(sessionContext.getChatId())
                .threadId(sessionContext.getThreadId())
                .userId(sessionContext.getUserId())
                .workspaceRoot(sessionContext.getWorkspaceRoot())
                .messageId(sessionContext.getMessageId())
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> buildToolContext(RunRequest request, RunRecord runRecord) {
        SessionContext sessionContext = request.getSessionContext();
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("__runId", runRecord.getRunId());
        context.put("__parentRunId", runRecord.getParentRunId());
        context.put("__source", runRecord.getSource());
        context.put("__platform", sessionContext.getPlatform());
        context.put("__chatId", sessionContext.getChatId());
        context.put("__threadId", sessionContext.getThreadId());
        context.put("__userId", sessionContext.getUserId());
        context.put("__workspaceRoot", sessionContext.getWorkspaceRoot());
        context.put("__messageId", sessionContext.getMessageId());
        context.put("__delegateDepth", Integer.valueOf(resolveDelegateDepth(sessionContext)));
        return context;
    }

    private int resolveDelegateDepth(SessionContext sessionContext) {
        if (sessionContext == null || sessionContext.getMetadata() == null) {
            return 0;
        }

        Object value = sessionContext.getMetadata().get("delegateDepth");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        return 0;
    }

    private void appendChildRun(RunRecord childRun) {
        if (StrUtil.isBlank(childRun.getParentRunId())) {
            return;
        }

        RunRecord parentRun = runStore.get(childRun.getParentRunId());
        if (parentRun == null) {
            return;
        }

        List<ChildRunRecord> children = parentRun.getChildren() == null
                ? new ArrayList<ChildRunRecord>()
                : new ArrayList<ChildRunRecord>(parentRun.getChildren());
        for (int i = children.size() - 1; i >= 0; i--) {
            if (childRun.getRunId().equals(children.get(i).getRunId())) {
                children.remove(i);
            }
        }

        children.add(ChildRunRecord.builder()
                .runId(childRun.getRunId())
                .parentRunId(childRun.getParentRunId())
                .status(childRun.getStatus())
                .summary(buildChildSummary(childRun))
                .startedAt(childRun.getStartedAt())
                .completedAt(childRun.getCompletedAt())
                .build());
        parentRun.setChildren(children);
        runStore.save(parentRun);
    }

    private String buildChildSummary(RunRecord childRun) {
        String summary = StrUtil.blankToDefault(childRun.getResponseText(), childRun.getErrorMessage());
        if (StrUtil.isBlank(summary)) {
            summary = childRun.getStatus() == null ? "completed" : childRun.getStatus().name().toLowerCase();
        }
        return StrUtil.maxLength(summary, 400);
    }

    private void dispatchReply(ReplyRoute replyRoute, String text) {
        if (replyRoute == null || StrUtil.isBlank(text)) {
            return;
        }

        for (ChannelAdapter adapter : channelAdapters) {
            if (adapter.enabled() && replyRoute.getPlatform().equals(adapter.platform())) {
                adapter.sendMessage(replyRoute, ChannelOutboundMessage.builder().text(text).build());
                return;
            }
        }
    }

    protected static class AgentExecution {
        private final String responseText;
        private final List<ToolCallRecord> toolCalls;

        protected AgentExecution(String responseText, List<ToolCallRecord> toolCalls) {
            this.responseText = responseText;
            this.toolCalls = toolCalls == null ? new ArrayList<ToolCallRecord>() : new ArrayList<ToolCallRecord>(toolCalls);
        }

        public String getResponseText() {
            return responseText;
        }

        public List<ToolCallRecord> getToolCalls() {
            return new ArrayList<ToolCallRecord>(toolCalls);
        }
    }
}
