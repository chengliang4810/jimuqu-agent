package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.agent.store.DedupStore;
import com.jimuqu.claw.agent.store.RouteStore;
import com.jimuqu.claw.agent.store.RunStore;
import com.jimuqu.claw.agent.store.SessionStore;
import com.jimuqu.claw.agent.store.file.FileDedupStore;
import com.jimuqu.claw.agent.store.file.FileRouteStore;
import com.jimuqu.claw.agent.store.file.FileRunStore;
import com.jimuqu.claw.agent.store.file.FileSessionStore;
import com.jimuqu.claw.agent.tool.ToolRegistry;
import com.jimuqu.claw.agent.workspace.DefaultWorkspacePromptService;
import com.jimuqu.claw.agent.workspace.WorkspaceLayout;
import com.jimuqu.claw.agent.workspace.WorkspacePromptService;
import com.jimuqu.claw.channel.ChannelAdapter;
import com.jimuqu.claw.config.ClawProperties;
import com.jimuqu.claw.provider.ModelConfigResolver;
import com.jimuqu.claw.provider.ProviderDialect;
import com.jimuqu.claw.provider.model.ResolvedModelConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.tool.FunctionTool;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;

public class DefaultRuntimeServiceTest {
    @Test
    public void handleRequestPersistsChildRunAndBackfillsParentSummary() {
        Path root = FileUtil.mkdir(FileUtil.file("target/test-workspace/runtime-delegate")).toPath().toAbsolutePath().normalize();
        try {
            WorkspaceLayout layout = new WorkspaceLayout(root.toString());
            layout.initialize();

            ClawProperties properties = new ClawProperties();
            properties.getRuntime().setSystemPromptResource(null);

            SessionStore sessionStore = new FileSessionStore(layout);
            RunStore runStore = new FileRunStore(layout);
            RouteStore routeStore = new FileRouteStore(layout);
            DedupStore dedupStore = new FileDedupStore(layout);
            WorkspacePromptService promptService = new DefaultWorkspacePromptService(layout, properties);
            ModelConfigResolver modelConfigResolver = new ModelConfigResolver(properties, Collections.<ProviderDialect>emptyList()) {
                @Override
                public ResolvedModelConfig resolve(String modelAlias) {
                    return ResolvedModelConfig.builder()
                            .modelAlias("test-model")
                            .build();
                }

                @Override
                public ChatModel buildChatModel(String modelAlias) {
                    throw new UnsupportedOperationException("Not used in this test");
                }
            };

            ToolRegistry toolRegistry = new ToolRegistry() {
                @Override
                public Collection<FunctionTool> allTools() {
                    return Collections.emptyList();
                }

                @Override
                public FunctionTool get(String name) {
                    return null;
                }
            };

            DefaultRuntimeService runtimeService = new DefaultRuntimeService(
                    layout,
                    properties,
                    sessionStore,
                    runStore,
                    routeStore,
                    dedupStore,
                    promptService,
                    modelConfigResolver,
                    toolRegistry,
                    new ArrayList<ChannelAdapter>()) {
                @Override
                protected AgentExecution executeAgent(RunRequest request, RunRecord runRecord, ResolvedModelConfig modelConfig, String systemPrompt) {
                    Assertions.assertEquals("child-run", runRecord.getRunId());
                    Assertions.assertEquals("parent-run", request.getParentRunId());
                    Assertions.assertEquals("delegate_task", request.getSource());
                    return new AgentExecution("child runtime result", Collections.emptyList());
                }
            };

            RunRecord parent = RunRecord.builder()
                    .runId("parent-run")
                    .sessionId("sess-parent")
                    .status(RunStatus.SUCCEEDED)
                    .createdAt(Instant.now())
                    .build();
            runStore.save(parent);

            SessionContext sessionContext = SessionContext.builder()
                    .sessionId("sess-child")
                    .platform("wecom")
                    .chatId("chat-1")
                    .threadId("thread-1")
                    .userId("user-1")
                    .workspaceRoot(root.toString())
                    .metadata(new LinkedHashMap<String, Object>())
                    .build();
            sessionContext.getMetadata().put("delegateDepth", Integer.valueOf(1));

            RunRequest request = RunRequest.builder()
                    .runId("child-run")
                    .parentRunId("parent-run")
                    .sessionContext(sessionContext)
                    .userMessage("delegate check")
                    .source("delegate_task")
                    .createdAt(Instant.now())
                    .build();

            RunRecord child = runtimeService.handleRequest(request);
            Assertions.assertEquals(RunStatus.SUCCEEDED, child.getStatus());
            Assertions.assertEquals("test-model", child.getModelAlias());
            Assertions.assertEquals("parent-run", child.getParentRunId());
            Assertions.assertEquals("child runtime result", child.getResponseText());

            RunRecord storedParent = runStore.get("parent-run");
            Assertions.assertEquals(1, storedParent.getChildren().size());
            Assertions.assertEquals("child-run", storedParent.getChildren().get(0).getRunId());
            Assertions.assertEquals(RunStatus.SUCCEEDED, storedParent.getChildren().get(0).getStatus());
            Assertions.assertTrue(String.valueOf(storedParent.getChildren().get(0).getSummary()).contains("child runtime result"));
            Assertions.assertNotNull(sessionStore.get("sess-child"));
        } finally {
            FileUtil.del(root.toFile());
        }
    }
}
