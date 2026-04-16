package com.jimuqu.agent.storage.repository;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SqliteDatabase 实现。
 */
public class SqliteDatabase {
    private final String jdbcUrl;

    public SqliteDatabase(AppConfig appConfig) throws SQLException {
        FileUtil.mkParentDirs(appConfig.getRuntime().getStateDb());
        this.jdbcUrl = "jdbc:sqlite:" + appConfig.getRuntime().getStateDb();
        initSchema();
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void initSchema() throws SQLException {
        Connection connection = openConnection();
        try {
            Statement statement = connection.createStatement();
            statement.execute("create table if not exists sessions (" +
                    "session_id text primary key," +
                    "source_key text not null," +
                    "branch_name text," +
                    "parent_session_id text," +
                    "model_override text," +
                    "ndjson text," +
                    "title text," +
                    "compressed_summary text," +
                    "system_prompt_snapshot text," +
                    "last_learning_at integer not null default 0," +
                    "last_compression_at integer not null default 0," +
                    "last_compression_input_tokens integer not null default 0," +
                    "compression_failure_count integer not null default 0," +
                    "last_compression_failed_at integer not null default 0," +
                    "created_at integer not null," +
                    "updated_at integer not null" +
                    ")");
            try {
                statement.execute("alter table sessions add column last_compression_at integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_compression_input_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column compression_failure_count integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_compression_failed_at integer not null default 0");
            } catch (Exception ignored) {
            }
            statement.execute("create index if not exists idx_sessions_source on sessions(source_key)");
            statement.execute("create virtual table if not exists sessions_fts using fts5(session_id, title, compressed_summary, ndjson)");
            statement.execute("create table if not exists bindings (" +
                    "source_key text primary key," +
                    "session_id text not null" +
                    ")");
            statement.execute("create table if not exists tool_toggles (" +
                    "source_key text not null," +
                    "tool_name text not null," +
                    "enabled integer not null," +
                    "primary key (source_key, tool_name)" +
                    ")");
            statement.execute("create table if not exists skill_states (" +
                    "source_key text not null," +
                    "skill_name text not null," +
                    "enabled integer not null," +
                    "primary key (source_key, skill_name)" +
                    ")");
            statement.execute("create table if not exists cron_jobs (" +
                    "job_id text primary key," +
                    "name text not null," +
                    "cron_expr text not null," +
                    "prompt text not null," +
                    "source_key text not null," +
                    "deliver_platform text," +
                    "deliver_chat_id text," +
                    "status text not null," +
                    "next_run_at integer not null," +
                    "last_run_at integer not null," +
                    "created_at integer not null," +
                    "updated_at integer not null" +
                    ")");
            statement.execute("create index if not exists idx_cron_jobs_source on cron_jobs(source_key)");
            statement.execute("create index if not exists idx_cron_jobs_next_run on cron_jobs(next_run_at)");
            statement.execute("create table if not exists cron_runs (" +
                    "run_id text primary key," +
                    "job_id text not null," +
                    "started_at integer not null," +
                    "finished_at integer," +
                    "status text not null," +
                    "summary text" +
                    ")");
            statement.execute("create table if not exists home_channels (" +
                    "platform text primary key," +
                    "chat_id text not null," +
                    "chat_name text," +
                    "updated_at integer not null" +
                    ")");
            statement.execute("create table if not exists approved_users (" +
                    "platform text not null," +
                    "user_id text not null," +
                    "user_name text," +
                    "approved_at integer not null," +
                    "approved_by text," +
                    "primary key (platform, user_id)" +
                    ")");
            statement.execute("create table if not exists pairing_requests (" +
                    "platform text not null," +
                    "code text not null," +
                    "user_id text not null," +
                    "user_name text," +
                    "chat_id text," +
                    "created_at integer not null," +
                    "expires_at integer not null," +
                    "primary key (platform, code)" +
                    ")");
            statement.execute("create table if not exists pairing_rate_limits (" +
                    "platform text not null," +
                    "user_id text not null," +
                    "requested_at integer not null," +
                    "failed_attempts integer not null," +
                    "lockout_until integer not null," +
                    "primary key (platform, user_id)" +
                    ")");
            statement.execute("create table if not exists platform_admins (" +
                    "platform text primary key," +
                    "user_id text not null," +
                    "user_name text," +
                    "chat_id text," +
                    "created_at integer not null" +
                    ")");
            statement.execute("create table if not exists checkpoints (" +
                    "checkpoint_id text primary key," +
                    "source_key text not null," +
                    "session_id text," +
                    "checkpoint_dir text not null," +
                    "manifest_path text not null," +
                    "created_at integer not null," +
                    "restored_at integer not null default 0" +
                    ")");
            statement.execute("create index if not exists idx_checkpoints_source_created on checkpoints(source_key, created_at desc)");
            statement.close();
        } finally {
            connection.close();
        }
    }
}
