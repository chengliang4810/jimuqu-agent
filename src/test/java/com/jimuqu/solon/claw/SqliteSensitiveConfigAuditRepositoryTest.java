package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.model.SensitiveConfigAuditEvent;
import com.jimuqu.solon.claw.storage.repository.SqliteDatabase;
import com.jimuqu.solon.claw.storage.repository.SqliteSensitiveConfigAuditRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SQLite 敏感配置追加式审计仓储测试。 */
class SqliteSensitiveConfigAuditRepositoryTest {
    /** 临时 SQLite 数据库。 */
    private SqliteDatabase database;

    /** 被测敏感配置审计仓储。 */
    private SqliteSensitiveConfigAuditRepository repository;

    /** 创建隔离的审计数据库。 */
    @BeforeEach
    void setUp() throws Exception {
        Path home = Files.createTempDirectory("sensitive-config-audit-test");
        AppConfig config = new AppConfig();
        config.getRuntime().setHome(home.toString());
        config.getRuntime().setStateDb(home.resolve("data/state.db").toString());
        database = new SqliteDatabase(config);
        repository = new SqliteSensitiveConfigAuditRepository(database);
    }

    /** 关闭临时数据库持有的共享连接。 */
    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    /** 审计表只保存八个必要元数据字段，不能保存配置值或请求凭据。 */
    @Test
    void shouldPersistOnlyRequiredAuditMetadata() throws Exception {
        SensitiveConfigAuditEvent event = event("event-metadata");

        repository.append(event);

        try (Connection connection = database.openReadConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select event_id, operation, actor_type, auth_method, profile, config_key, remote_ip, created_at "
                                        + "from sensitive_config_audit_events where event_id = ?")) {
            statement.setString(1, event.getEventId());
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("event_id")).isEqualTo("event-metadata");
                assertThat(resultSet.getString("operation")).isEqualTo("SECRET_REVEAL");
                assertThat(resultSet.getString("actor_type")).isEqualTo("DASHBOARD");
                assertThat(resultSet.getString("auth_method")).isEqualTo("BEARER");
                assertThat(resultSet.getString("profile")).isEqualTo("default");
                assertThat(resultSet.getString("config_key"))
                        .isEqualTo("solonclaw.gateway.injectionSecret");
                assertThat(resultSet.getString("remote_ip")).isEqualTo("127.0.0.1");
                assertThat(resultSet.getLong("created_at")).isEqualTo(123456L);
            }
        }

        assertThat(tableColumns())
                .containsExactly(
                        "event_id",
                        "operation",
                        "actor_type",
                        "auth_method",
                        "profile",
                        "config_key",
                        "remote_ip",
                        "created_at");
    }

    /** 重复事件标识必须失败且不得覆盖已经落库的审计事实。 */
    @Test
    void shouldRejectDuplicateEventIdWithoutReplacingOriginalRecord() throws Exception {
        SensitiveConfigAuditEvent original = event("event-duplicate");
        repository.append(original);
        SensitiveConfigAuditEvent replacement = event("event-duplicate");
        replacement.setOperation("SECRET_SET_ATTEMPT");

        assertThatThrownBy(() -> repository.append(replacement)).isInstanceOf(SQLException.class);

        assertThat(storedOperation("event-duplicate")).isEqualTo("SECRET_REVEAL");
    }

    /** 不完整事件必须显式失败，不能让调用方误以为审计已成功。 */
    @Test
    void shouldRejectIncompleteAuditEvent() {
        assertThatThrownBy(() -> repository.append(new SensitiveConfigAuditEvent()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sensitive configuration audit event is incomplete.");
    }

    /** 创建满足仓储完整性约束的审计事件。 */
    private SensitiveConfigAuditEvent event(String eventId) {
        SensitiveConfigAuditEvent event = new SensitiveConfigAuditEvent();
        event.setEventId(eventId);
        event.setOperation("SECRET_REVEAL");
        event.setActorType("DASHBOARD");
        event.setAuthMethod("BEARER");
        event.setProfile("default");
        event.setConfigKey("solonclaw.gateway.injectionSecret");
        event.setRemoteIp("127.0.0.1");
        event.setCreatedAt(123456L);
        return event;
    }

    /** 读取敏感配置审计表的实际列顺序。 */
    private List<String> tableColumns() throws Exception {
        List<String> columns = new ArrayList<String>();
        try (Connection connection = database.openReadConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet =
                        statement.executeQuery(
                                "pragma table_info(sensitive_config_audit_events)")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    /** 读取指定事件当前保存的操作类型。 */
    private String storedOperation(String eventId) throws Exception {
        try (Connection connection = database.openReadConnection();
                PreparedStatement statement =
                        connection.prepareStatement(
                                "select operation from sensitive_config_audit_events where event_id = ?")) {
            statement.setString(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("operation") : null;
            }
        }
    }
}
