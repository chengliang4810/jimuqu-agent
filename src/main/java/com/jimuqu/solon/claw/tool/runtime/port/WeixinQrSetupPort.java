package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层使用微信二维码配置能力的同步端口。 */
public interface WeixinQrSetupPort {
    /** 启动微信二维码配置。 */
    Map<String, Object> start();

    /** 查询微信二维码配置状态。 */
    Map<String, Object> get(String ticket);
}
