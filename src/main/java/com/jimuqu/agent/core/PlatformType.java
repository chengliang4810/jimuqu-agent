package com.jimuqu.agent.core;

public enum PlatformType {
    MEMORY,
    FEISHU,
    DINGTALK,
    WECOM,
    WEIXIN;

    public static PlatformType fromName(String name) {
        if (name == null) {
            return MEMORY;
        }

        String normalized = name.trim().toUpperCase();
        for (PlatformType value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }

        return MEMORY;
    }
}
