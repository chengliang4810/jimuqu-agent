package com.jimuqu.solon.claw.storage.repository;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.SensitiveConfigAuditEvent;
import com.jimuqu.solon.claw.core.repository.SensitiveConfigAuditRepository;
import java.sql.Connection;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;

/** SQLite Dashboard 敏感配置追加式审计仓储。 */
@RequiredArgsConstructor
public class SqliteSensitiveConfigAuditRepository extends SqliteRepositorySupport
        implements SensitiveConfigAuditRepository {
    /** 持有敏感配置审计表的 SQLite 数据库。 */
    private final SqliteDatabase database;

    /** 获取受单写锁保护的 SQLite 连接。 */
    @Override
    protected Connection getConnection() throws SQLException {
        return database.openConnection();
    }

    /**
     * 追加一条敏感配置审计事件，重复事件标识必须由 SQLite 主键约束拒绝。
     *
     * @param event 待持久化的审计事件。
     */
    @Override
    public void append(SensitiveConfigAuditEvent event) throws SQLException {
        validate(event);
        executeUpdate(
                "insert into sensitive_config_audit_events "
                        + "(event_id, operation, actor_type, auth_method, profile, config_key, remote_ip, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, event.getEventId());
                    statement.setString(2, event.getOperation());
                    statement.setString(3, event.getActorType());
                    statement.setString(4, event.getAuthMethod());
                    statement.setString(5, event.getProfile());
                    statement.setString(6, event.getConfigKey());
                    statement.setString(7, event.getRemoteIp());
                    statement.setLong(8, event.getCreatedAt());
                });
    }

    /**
     * 校验追加式审计事件的最小完整性，禁止静默丢弃不完整记录。
     *
     * @param event 待校验的审计事件。
     */
    private void validate(SensitiveConfigAuditEvent event) {
        if (event == null
                || StrUtil.isBlank(event.getEventId())
                || StrUtil.isBlank(event.getOperation())
                || StrUtil.isBlank(event.getActorType())
                || StrUtil.isBlank(event.getAuthMethod())
                || StrUtil.isBlank(event.getProfile())
                || StrUtil.isBlank(event.getConfigKey())
                || StrUtil.isBlank(event.getRemoteIp())
                || event.getCreatedAt() <= 0L) {
            throw new IllegalArgumentException(
                    "Sensitive configuration audit event is incomplete.");
        }
    }
}
