package com.jimuqu.agent;

import com.jimuqu.agent.core.model.DeliveryRequest;
import com.jimuqu.agent.support.AttachmentCacheService;
import com.jimuqu.agent.support.TestEnvironment;
import com.jimuqu.agent.tool.runtime.MessagingTools;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class MessagingToolsAttachmentTest {
    @Test
    void shouldDeliverMediaPathsAsAttachments() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools = new MessagingTools(
                env.deliveryService,
                "MEMORY:chat-1:user-1",
                new AttachmentCacheService(env.appConfig)
        );

        Path tempDir = Files.createTempDirectory("jimuqu-media-tool");
        File image = tempDir.resolve("demo.png").toFile();
        File voice = tempDir.resolve("note.silk").toFile();
        Files.write(image.toPath(), new byte[]{1, 2, 3});
        Files.write(voice.toPath(), new byte[]{4, 5, 6});

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            tools.sendMessage(null, null, "请发送附件", Arrays.asList("demo.png", voice.getAbsolutePath()));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        DeliveryRequest request = env.memoryChannelAdapter.getLastRequest();
        assertThat(request.getText()).isEqualTo("请发送附件");
        assertThat(request.getAttachments()).hasSize(2);
        assertThat(request.getAttachments().get(0).getKind()).isEqualTo("image");
        assertThat(request.getAttachments().get(0).getLocalPath()).endsWith("demo.png");
        assertThat(request.getAttachments().get(1).getKind()).isEqualTo("voice");
        assertThat(request.getAttachments().get(1).getLocalPath()).endsWith("note.silk");
    }

    @Test
    void shouldAllowTextOnlyWithoutAttachments() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        MessagingTools tools = new MessagingTools(
                env.deliveryService,
                "MEMORY:chat-1:user-1",
                new AttachmentCacheService(env.appConfig)
        );

        tools.sendMessage(null, null, "纯文本", Collections.<String>emptyList());

        DeliveryRequest request = env.memoryChannelAdapter.getLastRequest();
        assertThat(request.getText()).isEqualTo("纯文本");
        assertThat(request.getAttachments()).isEmpty();
    }
}
