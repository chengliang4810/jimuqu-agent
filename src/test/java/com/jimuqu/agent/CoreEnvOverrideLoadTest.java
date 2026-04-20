package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.Props;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

public class CoreEnvOverrideLoadTest {
    @Test
    void shouldLoadCoreAndChannelConfigFromRuntimeEnv() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-agent-core-env").toFile();
        File envFile = new File(runtimeHome, ".env");
        FileUtil.writeUtf8String(
                "JIMUQU_SCHEDULER_ENABLED=false\n"
                        + "JIMUQU_SCHEDULER_TICK_SECONDS=15\n"
                        + "JIMUQU_COMPRESSION_ENABLED=false\n"
                        + "JIMUQU_COMPRESSION_THRESHOLD_PERCENT=0.75\n"
                        + "JIMUQU_FEISHU_ENABLED=true\n"
                        + "JIMUQU_FEISHU_WEBSOCKET_URL=wss://feishu.example/ws\n"
                        + "JIMUQU_DINGTALK_STREAM_URL=wss://dingtalk.example/stream\n"
                        + "JIMUQU_WECOM_WEBSOCKET_URL=wss://wecom.example/ws\n"
                        + "JIMUQU_WECOM_GROUP_MEMBER_ALLOW_MAP_JSON={\"room-a\":[\"alice\",\"bob\"],\"*\":[\"admin\"]}\n"
                        + "JIMUQU_WEIXIN_ENABLED=true\n"
                        + "JIMUQU_WEIXIN_BASE_URL=https://weixin.example\n"
                        + "JIMUQU_WEIXIN_CDN_BASE_URL=https://cdn.example\n"
                        + "JIMUQU_WEIXIN_LONG_POLL_URL=https://poll.example/ilink/bot/getupdates\n"
                        + "JIMUQU_WEIXIN_SPLIT_MULTILINE_MESSAGES=true\n"
                        + "JIMUQU_WEIXIN_SEND_CHUNK_RETRIES=9\n",
                envFile
        );

        Props props = new Props();
        props.put("jimuqu.runtime.home", runtimeHome.getAbsolutePath());
        props.put("jimuqu.runtime.contextDir", new File(runtimeHome, "context").getAbsolutePath());
        props.put("jimuqu.runtime.skillsDir", new File(runtimeHome, "skills").getAbsolutePath());
        props.put("jimuqu.runtime.cacheDir", new File(runtimeHome, "cache").getAbsolutePath());
        props.put("jimuqu.runtime.stateDb", new File(runtimeHome, "state.db").getAbsolutePath());
        props.put("jimuqu.scheduler.enabled", "true");
        props.put("jimuqu.channels.feishu.enabled", "false");
        props.put("jimuqu.channels.weixin.enabled", "false");

        AppConfig config = AppConfig.load(props);

        assertThat(config.getScheduler().isEnabled()).isFalse();
        assertThat(config.getScheduler().getTickSeconds()).isEqualTo(15);
        assertThat(config.getCompression().isEnabled()).isFalse();
        assertThat(config.getCompression().getThresholdPercent()).isEqualTo(0.75D);

        assertThat(config.getChannels().getFeishu().isEnabled()).isTrue();
        assertThat(config.getChannels().getFeishu().getWebsocketUrl()).isEqualTo("wss://feishu.example/ws");
        assertThat(config.getChannels().getDingtalk().getStreamUrl()).isEqualTo("wss://dingtalk.example/stream");
        assertThat(config.getChannels().getWecom().getWebsocketUrl()).isEqualTo("wss://wecom.example/ws");
        assertThat(config.getChannels().getWecom().getGroupMemberAllowedUsers().get("room-a")).containsExactly("alice", "bob");
        assertThat(config.getChannels().getWecom().getGroupMemberAllowedUsers().get("*")).containsExactly("admin");
        assertThat(config.getChannels().getWeixin().isEnabled()).isTrue();
        assertThat(config.getChannels().getWeixin().getBaseUrl()).isEqualTo("https://weixin.example");
        assertThat(config.getChannels().getWeixin().getCdnBaseUrl()).isEqualTo("https://cdn.example");
        assertThat(config.getChannels().getWeixin().getLongPollUrl()).isEqualTo("https://poll.example/ilink/bot/getupdates");
        assertThat(config.getChannels().getWeixin().isSplitMultilineMessages()).isTrue();
        assertThat(config.getChannels().getWeixin().getSendChunkRetries()).isEqualTo(9);
    }
}
