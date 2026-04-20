package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.agent.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.agent.storage.repository.SqliteDatabase;
import com.jimuqu.agent.support.AttachmentCacheService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DingTalkPrivateSendLiveTest {
    @Test
    void shouldTryPrivateChatSend() throws Exception {
        String clientId = System.getenv("JIMUQU_DINGTALK_CLIENT_ID");
        String clientSecret = System.getenv("JIMUQU_DINGTALK_CLIENT_SECRET");
        String robotCode = System.getenv("JIMUQU_DINGTALK_ROBOT_CODE");
        String openConversationId = System.getenv("JIMUQU_DINGTALK_PRIVATE_OPEN_CONVERSATION_ID");
        String userId = System.getenv("JIMUQU_DINGTALK_PRIVATE_USER_ID");

        Assumptions.assumeTrue(clientId != null && clientId.trim().length() > 0);
        Assumptions.assumeTrue(clientSecret != null && clientSecret.trim().length() > 0);
        Assumptions.assumeTrue(robotCode != null && robotCode.trim().length() > 0);
        Assumptions.assumeTrue(openConversationId != null && openConversationId.trim().length() > 0);
        Assumptions.assumeTrue(userId != null && userId.trim().length() > 0);

        AppConfig.ChannelConfig config = new AppConfig.ChannelConfig();
        config.setEnabled(true);
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        config.setRobotCode(robotCode);
        AppConfig appConfig = buildAppConfig(config, Files.createTempDirectory("jimuqu-dingtalk-live").toFile());
        ChannelStateRepository channelStateRepository = new SqliteChannelStateRepository(new SqliteDatabase(appConfig));
        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(config, channelStateRepository, new AttachmentCacheService(appConfig));
        adapter.connect();
        try {
            Field field = DingTalkChannelAdapter.class.getDeclaredField("conversationGroupFlags");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Boolean> flags = (Map<String, Boolean>) field.get(adapter);
            flags.put(openConversationId, Boolean.FALSE);
            DeliveryRequest request = new DeliveryRequest();
            request.setPlatform(PlatformType.DINGTALK);
            request.setChatId(openConversationId);
            request.setUserId(userId);
            request.setChatType("dm");
            request.setText("私聊发送测试");
            adapter.send(request);
            assertThat(adapter.isConnected()).isTrue();
        } finally {
            adapter.disconnect();
        }
    }

    private static AppConfig buildAppConfig(AppConfig.ChannelConfig channelConfig, java.io.File runtimeHome) {
        AppConfig appConfig = new AppConfig();
        appConfig.getChannels().setDingtalk(channelConfig);
        appConfig.getRuntime().setHome(runtimeHome.getAbsolutePath());
        appConfig.getRuntime().setContextDir(new java.io.File(runtimeHome, "context").getAbsolutePath());
        appConfig.getRuntime().setSkillsDir(new java.io.File(runtimeHome, "skills").getAbsolutePath());
        appConfig.getRuntime().setCacheDir(new java.io.File(runtimeHome, "cache").getAbsolutePath());
        appConfig.getRuntime().setStateDb(new java.io.File(runtimeHome, "state.db").getAbsolutePath());
        appConfig.normalizePaths();
        return appConfig;
    }
}

