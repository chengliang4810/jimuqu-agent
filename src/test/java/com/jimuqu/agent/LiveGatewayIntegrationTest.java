package com.jimuqu.agent;

import com.jimuqu.agent.core.GatewayMessage;
import com.jimuqu.agent.core.GatewayReply;
import com.jimuqu.agent.core.PlatformType;
import com.jimuqu.agent.core.SessionRecord;
import com.jimuqu.agent.support.MessageSupport;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.noear.solon.ai.chat.ChatRole;
import org.noear.solon.ai.chat.message.ChatMessage;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class LiveGatewayIntegrationTest {
    @Test
    void shouldRunGatewayFlowAgainstRealResponsesModel() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("JIMUQU_LIVE_AI_ENABLED")));
        Assumptions.assumeTrue(System.getenv("JIMUQU_LIVE_AI_KEY") != null && System.getenv("JIMUQU_LIVE_AI_KEY").trim().length() > 0);

        TestEnvironment env = TestEnvironment.withLiveLlm();
        GatewayReply claimPrompt = env.send("live-room", "tester", "hello");
        assertThat(claimPrompt.getContent()).contains("/pairing claim-admin");
        GatewayReply claimReply = env.send("live-room", "tester", "/pairing claim-admin");
        assertThat(claimReply.getContent()).contains("唯一管理员");
        GatewayMessage source = env.message("live-room", "tester", "请用一句话介绍你自己。");

        GatewayReply first = env.gatewayService.handle(source);
        assertThat(first.getContent()).isNotBlank();
        assertThat(env.memoryChannelAdapter.getLastRequest().getText()).isEqualTo(first.getContent());

        GatewayReply branch = env.gatewayService.handle(env.message("live-room", "tester", "/branch live"));
        assertThat(branch.getBranchName()).isEqualTo("live");

        GatewayReply toolReply = env.gatewayService.handle(new GatewayMessage(
                PlatformType.MEMORY,
                "live-room",
                "tester",
                "你必须调用 todo 工具，action=add，value=集成测试；然后再次调用 todo 工具 action=list，并告诉我结果。"));
        assertThat(toolReply.getContent()).isNotBlank();

        SessionRecord current = env.sessionRepository.getBoundSession("MEMORY:live-room:tester");
        List<ChatMessage> messages = MessageSupport.loadMessages(current.getNdjson());
        boolean hasToolMessage = false;
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatRole.TOOL) {
                hasToolMessage = true;
                break;
            }
        }
        assertThat(hasToolMessage).isTrue();

        GatewayReply cronCreate = env.gatewayService.handle(new GatewayMessage(
                PlatformType.MEMORY,
                "live-room",
                "tester",
                "/cron create livejob|*/5 * * * *|请只回复：live cron ok"));
        Matcher matcher = Pattern.compile("([a-f0-9]{32})").matcher(cronCreate.getContent());
        assertThat(matcher.find()).isTrue();
        String jobId = matcher.group(1);

        GatewayReply cronRun = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "live-room", "tester", "/cron run " + jobId));
        assertThat(cronRun.getContent()).contains("Executed cron job");

        GatewayReply retry = env.gatewayService.handle(new GatewayMessage(PlatformType.MEMORY, "live-room", "tester", "/retry"));
        assertThat(retry.getContent()).isNotBlank();
    }
}
