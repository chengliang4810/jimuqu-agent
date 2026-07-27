package com.jimuqu.solon.claw.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dashboard 敏感配置操作的追加式审计事件。 */
@Getter
@Setter
@NoArgsConstructor
public class SensitiveConfigAuditEvent {
    /** 审计事件唯一标识。 */
    private String eventId;

    /** 敏感配置操作类型。 */
    private String operation;

    /** 发起操作的安全主体类型。 */
    private String actorType;

    /** 请求使用的认证方式。 */
    private String authMethod;

    /** 操作目标 Profile 名。 */
    private String profile;

    /** 操作目标配置键，不包含配置值。 */
    private String configKey;

    /** 由服务端连接直接观察到的远端 IP。 */
    private String remoteIp;

    /** 审计事件创建时间戳，单位为毫秒。 */
    private long createdAt;
}
