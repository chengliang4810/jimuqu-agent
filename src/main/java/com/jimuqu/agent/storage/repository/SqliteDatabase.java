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
            statement.execute("create table if not exists projects (" +
                    "project_id text primary key," +
                    "slug text not null unique," +
                    "title text not null," +
                    "goal text," +
                    "status text not null," +
                    "current_todo_id text," +
                    "created_at integer not null," +
                    "updated_at integer not null" +
                    ")");
            statement.execute("create index if not exists idx_projects_slug on projects(slug)");
            statement.execute("create table if not exists project_agents (" +
                    "project_id text not null," +
                    "agent_name text not null," +
                    "role_hint text," +
                    "created_at integer not null," +
                    "updated_at integer not null," +
                    "primary key (project_id, agent_name)" +
                    ")");
            statement.execute("create table if not exists project_todos (" +
                    "todo_id text primary key," +
                    "project_id text not null," +
                    "parent_todo_id text," +
                    "todo_no text not null," +
                    "title text not null," +
                    "description text," +
                    "status text not null," +
                    "assigned_agent text," +
                    "priority text," +
                    "sort_order integer not null," +
                    "created_at integer not null," +
                    "updated_at integer not null," +
                    "finished_at integer not null default 0" +
                    ")");
            statement.execute("create index if not exists idx_project_todos_project_status on project_todos(project_id, status)");
            statement.execute("create index if not exists idx_project_todos_parent on project_todos(parent_todo_id)");
            statement.execute("create table if not exists project_runs (" +
                    "run_id text primary key," +
                    "project_id text not null," +
                    "todo_id text," +
                    "agent_name text," +
                    "session_id text," +
                    "work_dir text," +
                    "model text," +
                    "allowed_tools_json text," +
                    "loaded_memory_files_json text," +
                    "status text not null," +
                    "summary text," +
                    "started_at integer not null," +
                    "finished_at integer not null default 0" +
                    ")");
            statement.execute("create index if not exists idx_project_runs_project on project_runs(project_id, started_at desc)");
            statement.execute("create table if not exists project_questions (" +
                    "question_id text primary key," +
                    "project_id text not null," +
                    "todo_id text," +
                    "asked_by text," +
                    "question text not null," +
                    "answer text," +
                    "status text not null," +
                    "created_at integer not null," +
                    "answered_at integer not null default 0" +
                    ")");
            statement.execute("create index if not exists idx_project_questions_project_status on project_questions(project_id, status)");
            statement.execute("create table if not exists project_events (" +
                    "event_id text primary key," +
                    "project_id text not null," +
                    "todo_id text," +
                    "event_type text not null," +
                    "actor text," +
                    "message text," +
                    "metadata_json text," +
                    "created_at integer not null" +
                    ")");
            statement.execute("create index if not exists idx_project_events_project_time on project_events(project_id, created_at desc)");
            statement.close();
        } finally {
            connection.close();
        }
    }
}
