package com.jimuqu.claw.agent.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunRequest {
    private String runId;
    private String parentRunId;
    private SessionContext sessionContext;
    private ReplyRoute replyRoute;
    private String userMessage;
    private String systemPrompt;
    private String modelAlias;
    private String source;
    @Builder.Default
    private List<String> skillNames = new ArrayList<String>();
    @Builder.Default
    private Instant createdAt = Instant.now();
}
