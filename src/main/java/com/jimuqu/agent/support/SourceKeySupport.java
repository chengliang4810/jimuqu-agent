package com.jimuqu.agent.support;

import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.model.DeliveryRequest;

/**
 * 来源键转换辅助类。
 */
public final class SourceKeySupport {
    private SourceKeySupport() {
    }

    /**
     * 将来源键转换为投递请求。
     */
    public static DeliveryRequest toDeliveryRequest(String sourceKey, String text) {
        String[] parts = split(sourceKey);
        return new DeliveryRequest(PlatformType.fromName(parts[0]), parts[1], parts[2], null, null, text);
    }

    /**
     * 拆分 `platform:chatId:userId` 结构的来源键。
     */
    public static String[] split(String sourceKey) {
        String[] out = new String[]{PlatformType.MEMORY.name(), "", ""};
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
