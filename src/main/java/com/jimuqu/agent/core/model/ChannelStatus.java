package com.jimuqu.agent.core.model;

import com.jimuqu.agent.core.enums.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 渠道运行状态快照。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelStatus {
    /**
     * 所属平台。
     */
    private PlatformType platform;

    /**
     * 是否已启用。
     */
    private boolean enabled;

    /**
     * 是否已经建立连接。
     */
    private boolean connected;

    /**
     * 连接详情说明。
     */
    private String detail;
}
