package com.jimuqu.claw.agent.runtime.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplyRoute {
    private String platform;
    private String chatId;
    private String threadId;
}
