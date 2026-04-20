package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.service.ChannelAdapter;
import com.jimuqu.agent.gateway.platform.feishu.FeishuChannelAdapter;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.Context;

import java.util.Map;

/**
 * 飞书 webhook 入口。
 */
@Controller
public class FeishuWebhookController {
    private final Map<PlatformType, ChannelAdapter> channelAdapters;

    public FeishuWebhookController(Map<PlatformType, ChannelAdapter> channelAdapters) {
        this.channelAdapters = channelAdapters;
    }

    @Mapping("/feishu/webhook")
    public String webhook(Context context) throws Exception {
        ChannelAdapter adapter = channelAdapters.get(PlatformType.FEISHU);
        if (!(adapter instanceof FeishuChannelAdapter)) {
            context.status(503);
            return "{\"code\":503,\"msg\":\"Feishu adapter unavailable\"}";
        }
        context.contentType("application/json;charset=UTF-8");
        return ((FeishuChannelAdapter) adapter).handleWebhook(context.body());
    }
}
