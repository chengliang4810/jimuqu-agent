package com.jimuqu.agent.core.model;

import com.jimuqu.agent.core.enums.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 统一消息投递请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryRequest {
    /**
     * 目标平台。
     */
    private PlatformType platform;

    /**
     * 目标会话 ID。
     */
    private String chatId;

    /**
     * 目标用户 ID。
     */
    private String userId;

    /**
     * 线程或话题 ID。
     */
    private String threadId;

    /**
     * 要投递的文本内容。
     */
    private String text;
}
