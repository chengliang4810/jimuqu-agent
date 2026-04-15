package com.jimuqu.agent.support;

import com.jimuqu.agent.core.DeliveryRequest;
import com.jimuqu.agent.core.PlatformType;

public final class SourceKeySupport {
    private SourceKeySupport() {
    }

    public static DeliveryRequest toDeliveryRequest(String sourceKey, String text) {
        String[] parts = split(sourceKey);
        return new DeliveryRequest(PlatformType.fromName(parts[0]), parts[1], parts[2], null, text);
    }

    public static String[] split(String sourceKey) {
        String[] out = new String[]{"MEMORY", "", ""};
        if (sourceKey == null) {
            return out;
        }

        String[] parts = sourceKey.split(":", 3);
        for (int i = 0; i < parts.length && i < out.length; i++) {
            out[i] = parts[i];
        }

        return out;
    }
}
