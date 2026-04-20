package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.agent.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.agent.storage.repository.SqliteDatabase;
import com.jimuqu.agent.support.AttachmentCacheService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class DingTalkStreamConnectionLiveTest {
    @Test
    void shouldConnectToDingTalkStreamMode() throws Exception {
        String clientId = System.getenv("JIMUQU_DINGTALK_CLIENT_ID");
        String clientSecret = System.getenv("JIMUQU_DINGTALK_CLIENT_SECRET");
        String robotCode = System.getenv("JIMUQU_DINGTALK_ROBOT_CODE");

        Assumptions.assumeTrue(clientId != null && clientId.trim().length() > 0);
        Assumptions.assumeTrue(clientSecret != null && clientSecret.trim().length() > 0);
        Assumptions.assumeTrue(robotCode != null && robotCode.trim().length() > 0);

        AppConfig.ChannelConfig config = new AppConfig.ChannelConfig();
        config.setEnabled(true);
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRobotCode(robotCode);
        AppConfig appConfig = new AppConfig();
        java.io.File runtimeHome = Files.createTempDirectory("jimuqu-dingtalk-stream").toFile();
        appConfig.getChannels().setDingtalk(config);
        appConfig.getRuntime().setHome(runtimeHome.getAbsolutePath());
        appConfig.getRuntime().setContextDir(new java.io.File(runtimeHome, "context").getAbsolutePath());
        appConfig.getRuntime().setSkillsDir(new java.io.File(runtimeHome, "skills").getAbsolutePath());
        appConfig.getRuntime().setCacheDir(new java.io.File(runtimeHome, "cache").getAbsolutePath());
        appConfig.getRuntime().setStateDb(new java.io.File(runtimeHome, "state.db").getAbsolutePath());
        appConfig.normalizePaths();
        ChannelStateRepository channelStateRepository = new SqliteChannelStateRepository(new SqliteDatabase(appConfig));

        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(config, channelStateRepository, new AttachmentCacheService(appConfig));
        boolean connected = adapter.connect();
        try {
            assertThat(connected).isTrue();
            assertThat(adapter.isConnected()).isTrue();
            assertThat(adapter.detail()).contains("connected");
        } finally {
            adapter.disconnect();
        }
    }
}

