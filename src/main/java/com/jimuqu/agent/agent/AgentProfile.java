package com.jimuqu.agent.agent;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AgentProfile {
    private String agentName;
    private String rolePrompt;
    private String model;
    private String allowedToolsJson;
    private String skillsJson;
    private String memory;
    private long createdAt;
    private long updatedAt;
}
