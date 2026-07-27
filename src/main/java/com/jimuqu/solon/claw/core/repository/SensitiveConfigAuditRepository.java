package com.jimuqu.solon.claw.core.repository;

import com.jimuqu.solon.claw.core.model.SensitiveConfigAuditEvent;

/** Dashboard 敏感配置操作的追加式审计仓储。 */
public interface SensitiveConfigAuditRepository {
    /**
     * 追加一条不可覆盖的敏感配置审计事件。
     *
     * @param event 待持久化的审计事件。
     */
    void append(SensitiveConfigAuditEvent event) throws Exception;
}
