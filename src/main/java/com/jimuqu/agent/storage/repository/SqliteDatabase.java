package com.jimuqu.agent.storage.repository;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SqliteDatabase 实现。
 */
public class SqliteDatabase {
    private final String jdbcUrl;
    private final ReentrantLock connectionLock = new ReentrantLock(true);
    private Connection sharedConnection;

    public SqliteDatabase(AppConfig appConfig) throws SQLException {
        FileUtil.mkParentDirs(appConfig.getRuntime().getStateDb());
        this.jdbcUrl = "jdbc:sqlite:" + appConfig.getRuntime().getStateDb();
        initSchema();
    }

    public Connection openConnection() throws SQLException {
        connectionLock.lock();
        try {
            return lockReleasingConnection(sharedConnection());
        } catch (SQLException e) {
            connectionLock.unlock();
            throw e;
        } catch (RuntimeException e) {
            connectionLock.unlock();
            throw e;
        }
    }

    public void shutdown() {
        connectionLock.lock();
        try {
            closeQuietly(sharedConnection);
            sharedConnection = null;
        } finally {
            connectionLock.unlock();
        }
    }

    private Connection sharedConnection() throws SQLException {
        if (sharedConnection == null || sharedConnection.isClosed()) {
            try {
                sharedConnection = DriverManager.getConnection(jdbcUrl);
                Statement statement = sharedConnection.createStatement();
                try {
                    statement.execute("pragma busy_timeout=5000");
                    statement.execute("pragma journal_mode=WAL");
                } finally {
                    statement.close();
                }
            } catch (SQLException e) {
                closeQuietly(sharedConnection);
                sharedConnection = null;
                throw e;
            }
        }
        return sharedConnection;
    }

    private Connection lockReleasingConnection(final Connection delegate) {
        InvocationHandler handler = new InvocationHandler() {
            private boolean closed;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    if (!closed) {
                        closed = true;
                        connectionLock.unlock();
                    }
                    return null;
                }
                if ("isClosed".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    return Boolean.valueOf(closed || delegate.isClosed());
                }
                if (closed) {
                    throw new SQLException("Connection is closed");
                }
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                handler
        );
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception ignored) {
        }
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
                    "agent_snapshot_json text," +
                    "last_learning_at integer not null default 0," +
                    "last_compression_at integer not null default 0," +
                    "last_compression_input_tokens integer not null default 0," +
                    "compression_failure_count integer not null default 0," +
                    "last_compression_failed_at integer not null default 0," +
                    "last_input_tokens integer not null default 0," +
                    "last_output_tokens integer not null default 0," +
                    "last_reasoning_tokens integer not null default 0," +
                    "last_cache_read_tokens integer not null default 0," +
                    "last_total_tokens integer not null default 0," +
                    "cumulative_input_tokens integer not null default 0," +
                    "cumulative_output_tokens integer not null default 0," +
                    "cumulative_reasoning_tokens integer not null default 0," +
                    "cumulative_cache_read_tokens integer not null default 0," +
                    "cumulative_total_tokens integer not null default 0," +
                    "last_usage_at integer not null default 0," +
                    "last_resolved_provider text," +
                    "last_resolved_model text," +
                    "created_at integer not null," +
                    "updated_at integer not null" +
                    ")");
            try {
                statement.execute("alter table sessions add column branch_name text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column parent_session_id text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column model_override text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column ndjson text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column title text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column compressed_summary text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column system_prompt_snapshot text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column agent_snapshot_json text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_learning_at integer not null default 0");
            } catch (Exception ignored) {
            }
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
            try {
                statement.execute("alter table sessions add column last_input_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_output_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_reasoning_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_cache_read_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_total_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column cumulative_input_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column cumulative_output_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column cumulative_reasoning_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column cumulative_cache_read_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column cumulative_total_tokens integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_usage_at integer not null default 0");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_resolved_provider text");
            } catch (Exception ignored) {
            }
            try {
                statement.execute("alter table sessions add column last_resolved_model text");
            } catch (Exception ignored) {
            }
            statement.execute("create index if not exists idx_sessions_source on sessions(source_key)");
            try {
                ResultSetMetaSupport.close(statement.executeQuery("select tool_names from sessions_fts limit 1"));
            } catch (Exception ignored) {
                try {
                    statement.execute("drop table if exists sessions_fts");
                } catch (Exception ignoredDrop) {
                }
            }
            statement.execute("create virtual table if not exists sessions_fts using fts5(session_id, title, compressed_summary, ndjson, tool_names, tool_calls)");
            try {
                statement.execute("insert into sessions_fts (session_id, title, compressed_summary, ndjson, tool_names, tool_calls) " +
                        "select s.session_id, s.title, s.compressed_summary, s.ndjson, '', '' from sessions s " +
                        "where not exists (select 1 from sessions_fts f where f.session_id = s.session_id)");
            } catch (Exception ignored) {
            }
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
            statement.execute("create table if not exists global_settings (" +
                    "setting_key text primary key," +
                    "setting_value text not null," +
                    "updated_at integer not null" +
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
            statement.execute("create table if not exists agent_runs (" +
                    "run_id text primary key," +
                    "session_id text not null," +
                    "source_key text," +
                    "status text not null," +
                    "input_preview text," +
                    "final_reply_preview text," +
                    "provider text," +
                    "model text," +
                    "attempts integer not null default 0," +
                    "input_tokens integer not null default 0," +
                    "output_tokens integer not null default 0," +
                    "total_tokens integer not null default 0," +
                    "started_at integer not null," +
                    "finished_at integer not null default 0," +
                    "error text" +
                    ")");
            statement.execute("create index if not exists idx_agent_runs_session_started on agent_runs(session_id, started_at desc)");
            statement.execute("create table if not exists agent_run_events (" +
                    "event_id text primary key," +
                    "run_id text not null," +
                    "session_id text," +
                    "source_key text," +
                    "event_type text not null," +
                    "attempt_no integer not null default 0," +
                    "provider text," +
                    "model text," +
                    "summary text," +
                    "metadata_json text," +
                    "created_at integer not null" +
                    ")");
            statement.execute("create index if not exists idx_agent_run_events_run_time on agent_run_events(run_id, created_at asc)");
            statement.execute("create table if not exists channel_states (" +
                    "platform text not null," +
                    "scope_key text not null," +
                    "state_key text not null," +
                    "state_value text," +
                    "updated_at integer not null," +
                    "primary key (platform, scope_key, state_key)" +
                    ")");
            statement.execute("create index if not exists idx_channel_states_platform_scope on channel_states(platform, scope_key)");
            statement.execute("create table if not exists agent_profiles (" +
                    "agent_name text primary key," +
                    "role_prompt text," +
                    "model text," +
                    "allowed_tools_json text," +
                    "skills_json text," +
                    "memory text," +
                    "created_at integer not null," +
                    "updated_at integer not null" +
                    ")");
            statement.execute("drop table if exists project_events");
            statement.execute("drop table if exists project_questions");
            statement.execute("drop table if exists project_runs");
            statement.execute("drop table if exists project_todos");
            statement.execute("drop table if exists project_agents");
            statement.execute("drop table if exists projects");
            statement.close();
        } finally {
            connection.close();
        }
    }

    private static class ResultSetMetaSupport {
        private static void close(java.sql.ResultSet resultSet) {
            if (resultSet == null) {
                return;
            }
            try {
                resultSet.close();
            } catch (Exception ignored) {
            }
        }
    }
}
