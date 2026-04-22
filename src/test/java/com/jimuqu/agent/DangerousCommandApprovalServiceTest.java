package com.jimuqu.agent;

import com.jimuqu.agent.core.enums.PlatformType;
import com.jimuqu.agent.support.TestEnvironment;
import com.jimuqu.agent.tool.runtime.DangerousCommandApprovalService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DangerousCommandApprovalServiceTest {
    @Test
    void shouldDetectDangerousShellCommand() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        DangerousCommandApprovalService.DetectionResult result = env.dangerousCommandApprovalService.detect(
                "execute_shell",
                "rm -rf runtime/cache"
        );

        assertThat(result).isNotNull();
        assertThat(result.getPatternKey()).isEqualTo("recursive_delete");
        assertThat(result.getDescription()).contains("recursive delete");
    }

    @Test
    void shouldIgnoreSafeShellCommand() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();

        DangerousCommandApprovalService.DetectionResult result = env.dangerousCommandApprovalService.detect(
                "execute_shell",
                "git status"
        );

        assertThat(result).isNull();
    }

    @Test
    void shouldBuildFeishuApprovalCardExtrasAndParseCardAction() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        DangerousCommandApprovalService.PendingApproval pending = new DangerousCommandApprovalService.PendingApproval();
        pending.setToolName("execute_shell");
        pending.setPatternKey("recursive_delete");
        pending.setDescription("recursive delete");
        pending.setCommand("rm -rf runtime/cache");

        Map<String, Object> extras = env.dangerousCommandApprovalService.buildDeliveryExtras(PlatformType.FEISHU, pending);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(DangerousCommandApprovalService.CARD_ACTION_KEY, DangerousCommandApprovalService.CARD_ACTION_APPROVE);
        payload.put(DangerousCommandApprovalService.CARD_SCOPE_KEY, "always");

        assertThat(extras.get("mode")).isEqualTo(DangerousCommandApprovalService.DELIVERY_MODE_APPROVAL_CARD);
        assertThat(extras.get("approvalCommand")).isEqualTo("rm -rf runtime/cache");
        assertThat(DangerousCommandApprovalService.commandFromCardActionPayload(payload)).isEqualTo("/approve always");
    }
}
