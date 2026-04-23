package com.jimuqu.agent.core.service;

import com.jimuqu.agent.core.model.GatewayMessage;
import com.jimuqu.agent.core.model.GatewayReply;

/**
 * Slash 命令处理接口。
 */
public interface CommandService {
    /**
     * 判断是否支持指定命令。
     */
    boolean supports(String commandName);

    /**
     * 处理命令文本。
     */
    GatewayReply handle(GatewayMessage message, String commandLine) throws Exception;

    /**
     * 处理命令文本，并向事件接收器输出运行过程。
     */
    default GatewayReply handle(GatewayMessage message, String commandLine, ConversationEventSink eventSink) throws Exception {
        return handle(message, commandLine);
    }
}
