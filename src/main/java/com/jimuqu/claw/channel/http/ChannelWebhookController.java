package com.jimuqu.claw.channel.http;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.runtime.RuntimeService;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.channel.ChannelAdapter;
import com.jimuqu.claw.channel.ReplyRouteSupport;
import com.jimuqu.claw.channel.model.ChannelInboundMessage;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Path;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.MethodType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@Mapping("/api/channels")
public class ChannelWebhookController {
    @Inject
    private RuntimeService runtimeService;

    @Inject
    private List<ChannelAdapter> channelAdapters;

    public ChannelWebhookController() {
    }

    public ChannelWebhookController(RuntimeService runtimeService, List<ChannelAdapter> channelAdapters) {
        this.runtimeService = runtimeService;
        this.channelAdapters = channelAdapters;
    }

    @Mapping(path = "/{platform}/inbound", method = MethodType.POST)
    public Object inbound(@Path("platform") String platform, Context ctx) throws IOException {
        ChannelAdapter adapter = findAdapter(platform);
        if (adapter == null) {
            ctx.status(404);
            return error("No channel adapter for platform: " + platform);
        }
        if (!adapter.enabled()) {
            ctx.status(403);
            return error("Channel adapter is disabled: " + platform);
        }

        try {
            ChannelInboundMessage inboundMessage = adapter.parseInbound(ctx.bodyNew());
            if (inboundMessage == null || inboundMessage.getSessionContext() == null) {
                ctx.status(400);
                return error("Inbound payload could not be parsed");
            }
            if (StrUtil.isBlank(inboundMessage.getText())) {
                ctx.status(400);
                return error("Inbound payload is missing text");
            }

            RunRecord runRecord = runtimeService.handleInbound(inboundMessage);
            return success(platform, inboundMessage, runRecord);
        } catch (IllegalArgumentException e) {
            ctx.status(400);
            return error(e.getMessage());
        }
    }

    @Mapping(path = "/{platform}/status", method = MethodType.GET)
    public Object status(@Path("platform") String platform, Context ctx) {
        ChannelAdapter adapter = findAdapter(platform);
        if (adapter == null) {
            ctx.status(404);
            return error("No channel adapter for platform: " + platform);
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("success", Boolean.TRUE);
        response.put("platform", adapter.platform());
        response.put("enabled", Boolean.valueOf(adapter.enabled()));
        return response;
    }

    private ChannelAdapter findAdapter(String platform) {
        if (StrUtil.isBlank(platform)) {
            return null;
        }

        List<ChannelAdapter> adapters = channelAdapters == null
                ? new ArrayList<ChannelAdapter>()
                : channelAdapters;
        for (ChannelAdapter adapter : adapters) {
            if (adapter != null && platform.equalsIgnoreCase(adapter.platform())) {
                return adapter;
            }
        }
        return null;
    }

    private Map<String, Object> success(String platform, ChannelInboundMessage inboundMessage, RunRecord runRecord) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("success", Boolean.TRUE);
        response.put("platform", platform);
        response.put("message_id", inboundMessage.getMessageId());
        response.put("session_id", inboundMessage.getSessionContext().getSessionId());
        response.put("reply_target", ReplyRouteSupport.format(inboundMessage.getReplyRoute()));
        response.put("run_id", runRecord.getRunId());
        response.put("status", runRecord.getStatus() == null ? null : runRecord.getStatus().name().toLowerCase());
        response.put("response_text", runRecord.getResponseText());
        response.put("error_message", runRecord.getErrorMessage());
        return response;
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("success", Boolean.FALSE);
        response.put("error", message);
        return response;
    }
}
