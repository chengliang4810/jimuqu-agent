package com.jimuqu.agent;

import com.jimuqu.agent.config.AppConfig;
import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.core.repository.ChannelStateRepository;
import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.gateway.platform.dingtalk.DingTalkChannelAdapter;
import com.jimuqu.agent.storage.repository.SqliteChannelStateRepository;
import com.jimuqu.agent.storage.repository.SqliteDatabase;
import com.jimuqu.agent.support.AttachmentCacheService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DingTalkAiCardRoutingTest {
    @Test
    void shouldRouteDeliveryRequestWithCardExtrasToAiCardSend() throws Exception {
        AppConfig config = new AppConfig();
        File runtimeHome = Files.createTempDirectory("jimuqu-agent-dingtalk-card").toFile();
        config.getRuntime().setHome(runtimeHome.getAbsolutePath());
        config.getRuntime().setContextDir(new File(runtimeHome, "context").getAbsolutePath());
        config.getRuntime().setSkillsDir(new File(runtimeHome, "skills").getAbsolutePath());
        config.getRuntime().setCacheDir(new File(runtimeHome, "cache").getAbsolutePath());
        config.getRuntime().setStateDb(new File(new File(runtimeHome, "data"), "state.db").getAbsolutePath());
        config.getChannels().getDingtalk().setEnabled(true);
        config.getChannels().getDingtalk().setClientId("app-key");
        config.getChannels().getDingtalk().setClientSecret("app-secret");
        config.getChannels().getDingtalk().setRobotCode("robot-code");

        ChannelStateRepository stateRepository = new SqliteChannelStateRepository(new SqliteDatabase(config));

        final DeliveryRequest[] captured = new DeliveryRequest[1];
        DingTalkChannelAdapter adapter = new DingTalkChannelAdapter(config.getChannels().getDingtalk(), stateRepository, new AttachmentCacheService(config)) {
            @Override
            protected synchronized void refreshAccessTokenIfNecessary() {
                // no-op for unit test
            }

            @Override
            protected void sendAiCard(DeliveryRequest request) {
                captured[0] = request;
            }
        };

        DeliveryRequest request = new DeliveryRequest();
        request.setPlatform(PlatformType.DINGTALK);
        request.setChatId("cid-group");
        request.setChatType("group");
        Map<String, Object> extras = new LinkedHashMap<String, Object>();
        extras.put("mode", "ai_card");
        extras.put("cardTemplateId", "tpl-001");
        extras.put("cardData", "{\"title\":\"demo\"}");
        request.setChannelExtras(extras);

        adapter.send(request);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getChannelExtras()).containsEntry("cardTemplateId", "tpl-001");
    }
}
