package com.jimuqu.claw.channel.http;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.ReplyRoute;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunRequest;
import com.jimuqu.claw.agent.runtime.model.SessionContext;
import com.jimuqu.claw.channel.ChannelPayloadSupport;
import com.jimuqu.claw.channel.ReplyRouteSupport;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@Mapping("/api/runtime")
public class RuntimeDebugController {
    @Inject
    private RuntimeService runtimeService;

    public RuntimeDebugController() {
    }

    public RuntimeDebugController(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Mapping(path = "/run", method = MethodType.POST)
    public Object run(Context ctx) throws IOException {
        try {
            Map<String, Object> payload = ChannelPayloadSupport.parseJsonObject(ctx.bodyNew());
            RunRequest request = buildRequest(payload);
            if (StrUtil.isBlank(request.getUserMessage())) {
                ctx.status(400);
                return error("text is required");
            }

            RunRecord runRecord = runtimeService.handleRequest(request);
            return success(runRecord);
        } catch (IllegalArgumentException e) {
            ctx.status(400);
            return error(e.getMessage());
        }
    }

    private RunRequest buildRequest(Map<String, Object> payload) {
        ReplyRoute replyRoute = resolveReplyRoute(payload);
        String platform = StrUtil.blankToDefault(
                ChannelPayloadSupport.string(payload, "platform", "session.platform"),
                replyRoute == null ? "debug" : replyRoute.getPlatform());

        String chatId = StrUtil.blankToDefault(
                ChannelPayloadSupport.string(payload, "chat_id", "chatId", "session.chat_id", "session.chatId"),
                replyRoute == null ? null : replyRoute.getChatId());
        String threadId = StrUtil.blankToDefault(
                ChannelPayloadSupport.string(payload, "thread_id", "threadId", "session.thread_id", "session.threadId"),
                replyRoute == null ? null : replyRoute.getThreadId());

        SessionContext sessionContext = SessionContext.builder()
                .sessionId(ChannelPayloadSupport.string(payload, "session_id", "sessionId", "session.session_id", "session.sessionId"))
                .platform(platform)
                .chatId(chatId)
                .threadId(threadId)
                .userId(ChannelPayloadSupport.string(payload, "user_id", "userId", "session.user_id", "session.userId"))
                .workspaceRoot(ChannelPayloadSupport.string(payload, "workspace_root", "workspaceRoot", "session.workspace_root", "session.workspaceRoot"))
                .messageId(ChannelPayloadSupport.string(payload, "message_id", "messageId"))
                .metadata(new LinkedHashMap<String, Object>())
                .build();

        List<String> skillNames = ChannelPayloadSupport.stringList(payload, "skill_names", "skillNames", "skills");

        return RunRequest.builder()
                .runId(ChannelPayloadSupport.string(payload, "run_id", "runId"))
                .parentRunId(ChannelPayloadSupport.string(payload, "parent_run_id", "parentRunId"))
                .sessionContext(sessionContext)
                .replyRoute(replyRoute)
                .userMessage(ChannelPayloadSupport.string(payload, "text", "message", "user_message", "userMessage"))
                .systemPrompt(ChannelPayloadSupport.string(payload, "system_prompt", "systemPrompt"))
                .modelAlias(ChannelPayloadSupport.string(payload, "model", "model_alias", "modelAlias"))
                .source(StrUtil.blankToDefault(ChannelPayloadSupport.string(payload, "source"), "debug-api"))
                .skillNames(skillNames)
                .build();
    }

    private ReplyRoute resolveReplyRoute(Map<String, Object> payload) {
        ReplyRoute route = ReplyRouteSupport.fromMap(
                ChannelPayloadSupport.map(payload, "reply_route", "replyRoute"),
                ChannelPayloadSupport.string(payload, "platform", "session.platform"));
        if (route != null) {
            return route;
        }

        return ReplyRouteSupport.parse(ChannelPayloadSupport.string(payload, "reply_target", "replyTarget", "deliver"));
    }

    private Map<String, Object> success(RunRecord runRecord) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("success", Boolean.TRUE);
        response.put("run_id", runRecord.getRunId());
        response.put("parent_run_id", runRecord.getParentRunId());
        response.put("session_id", runRecord.getSessionId());
        response.put("status", runRecord.getStatus() == null ? null : runRecord.getStatus().name().toLowerCase());
        response.put("response_text", runRecord.getResponseText());
        response.put("error_message", runRecord.getErrorMessage());
        response.put("tool_calls", runRecord.getToolCalls());
        return response;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("success", Boolean.FALSE);
        response.put("error", message);
        return response;
    }
}
