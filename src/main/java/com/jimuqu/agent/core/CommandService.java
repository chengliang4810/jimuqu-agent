package com.jimuqu.agent.core;

public interface CommandService {
    boolean supports(String commandName);

    GatewayReply handle(GatewayMessage message, String commandLine) throws Exception;
}
