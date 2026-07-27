package com.jimuqu.solon.claw.storage.repository;

import com.jimuqu.solon.claw.support.SecretRedactor;
import java.sql.PreparedStatement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SQLite 运行叶子存储共享支持，集中参数绑定、脱敏和可降级维护日志。 */
abstract class SqliteRunStoreSupport {
    /** 运行叶子存储日志只记录操作名与异常类型，避免泄露会话内容。 */
    private static final Logger log = LoggerFactory.getLogger(SqliteRunStoreSupport.class);

    /** SQLite 数据库连接入口。 */
    protected final SqliteDatabase database;

    /**
     * 创建运行叶子存储支持实例。
     *
     * @param database SQLite 数据库连接入口。
     */
    SqliteRunStoreSupport(SqliteDatabase database) {
        this.database = database;
    }

    /**
     * 按参数类型绑定动态查询参数。
     *
     * @param statement 待绑定的预编译语句。
     * @param args 查询参数列表。
     * @throws Exception 参数绑定失败时抛出。
     */
    protected void bindArgs(PreparedStatement statement, List<Object> args) throws Exception {
        for (int index = 0; index < args.size(); index++) {
            Object value = args.get(index);
            if (value instanceof Long) {
                statement.setLong(index + 1, ((Long) value).longValue());
            } else if (value instanceof Integer) {
                statement.setInt(index + 1, ((Integer) value).intValue());
            } else {
                statement.setString(index + 1, value == null ? null : String.valueOf(value));
            }
        }
    }

    /**
     * 记录可降级维护失败，避免非主链索引写入阻断业务数据保存。
     *
     * @param operation 维护操作名称。
     * @param error 维护异常。
     */
    protected void logBestEffortFailure(String operation, Exception error) {
        log.debug(
                "Agent run 叶子存储可降级维护失败，已跳过非主链更新: operation={}, error={}",
                operation,
                error == null ? "unknown" : error.getClass().getSimpleName());
    }

    /**
     * 脱敏文本中的密钥、令牌和敏感路径。
     *
     * @param value 待处理文本。
     * @param maxLength 最大保留字符数。
     * @return 返回脱敏后的文本。
     */
    protected String redact(String value, int maxLength) {
        return value == null
                ? null
                : SecretRedactor.redactSensitivePaths(SecretRedactor.redact(value, maxLength));
    }
}
