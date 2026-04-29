package com.jimuqu.agent.bootstrap;

import com.jimuqu.agent.agent.AgentProfileRepository;
import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.repository.CronJobRepository;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
import com.jimuqu.agent.core.repository.AgentRunRepository;
import com.jimuqu.agent.core.repository.GlobalSettingRepository;
import com.jimuqu.agent.core.repository.GatewayPolicyRepository;
import com.jimuqu.agent.core.repository.SessionRepository;
import com.jimuqu.agent.core.service.CheckpointService;
import com.jimuqu.agent.storage.repository.SqliteCronJobRepository;
import com.jimuqu.agent.storage.repository.SqliteAgentProfileRepository;
import com.jimuqu.agent.storage.repository.SqliteAgentRunRepository;
import com.jimuqu.agent.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.agent.storage.repository.SqliteDatabase;
import com.jimuqu.agent.storage.repository.SqliteGlobalSettingRepository;
import com.jimuqu.agent.storage.repository.SqliteGatewayPolicyRepository;
import com.jimuqu.agent.storage.repository.SqlitePreferenceStore;
import com.jimuqu.agent.storage.repository.SqliteSessionRepository;
import com.jimuqu.agent.support.DefaultCheckpointService;
import org.noear.solon.annotation.Bean;
import org.noear.solon.annotation.Configuration;

/**
 * storage bean configuration.
 */
@Configuration
public class StorageConfiguration {
    @Bean(destroyMethod = "shutdown")
    public SqliteDatabase sqliteDatabase(AppConfig appConfig) throws Exception {
        return new SqliteDatabase(appConfig);
    }

    @Bean
    public SqlitePreferenceStore sqlitePreferenceStore(SqliteDatabase sqliteDatabase) {
        return new SqlitePreferenceStore(sqliteDatabase);
    }

    @Bean
    public SessionRepository sessionRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteSessionRepository(sqliteDatabase);
    }

    @Bean
    public CronJobRepository cronJobRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteCronJobRepository(sqliteDatabase);
    }

    @Bean
    public AgentRunRepository agentRunRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteAgentRunRepository(sqliteDatabase);
    }

    @Bean
    public ChannelStateRepository channelStateRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteChannelStateRepository(sqliteDatabase);
    }

    @Bean
    public GlobalSettingRepository globalSettingRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteGlobalSettingRepository(sqliteDatabase);
    }

    @Bean
    public AgentProfileRepository agentProfileRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteAgentProfileRepository(sqliteDatabase);
    }

    @Bean
    public GatewayPolicyRepository gatewayPolicyRepository(SqliteDatabase sqliteDatabase) {
        return new SqliteGatewayPolicyRepository(sqliteDatabase);
    }

    @Bean
    public CheckpointService checkpointService(AppConfig appConfig, SqliteDatabase sqliteDatabase) {
        return new DefaultCheckpointService(appConfig, sqliteDatabase);
    }
}
