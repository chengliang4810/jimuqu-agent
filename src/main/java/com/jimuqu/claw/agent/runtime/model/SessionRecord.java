package com.jimuqu.claw.agent.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionRecord {
    private String sessionId;
    private String platform;
    private String chatId;
    private String threadId;
    private String userId;
    private Instant createdAt;
    private Instant updatedAt;
}
