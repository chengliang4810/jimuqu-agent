package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层使用国内渠道二维码配置能力的同步端口。 */
public interface DomesticQrSetupPort {
    /** 启动当前 Profile 的二维码配置。 */
    Map<String, Object> start(String channel);

    /** 启动指定 Profile 的二维码配置。 */
    Map<String, Object> start(String channel, String profile);

    /** 查询当前 Profile 的二维码配置状态。 */
    Map<String, Object> get(String ticket);

    /** 查询指定 Profile 的二维码配置状态。 */
    Map<String, Object> get(String ticket, String profile);
}
