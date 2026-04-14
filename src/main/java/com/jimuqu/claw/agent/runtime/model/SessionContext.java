package com.jimuqu.claw.agent.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContext {
    private String sessionId;
    private String platform;
    private String chatId;
    private String threadId;
    private String userId;
    private String workspaceRoot;
    private String messageId;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
