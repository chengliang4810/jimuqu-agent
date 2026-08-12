package com.jimuqu.solon.claw.gateway.feedback;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jimuqu.solon.claw.support.constants.ToolNameConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 凭据工具参数预览脱敏测试。 */
public class CredentialToolPreviewSupportTest {
    /** 验证 credential_manage 的 value 在详细预览中也不会泄漏。 */
    @Test
    public void shouldRedactCredentialValueFromVerbosePreview() {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        args.put("action", "set");
        args.put("name", "SSH_PASSWORD");
        args.put("value", "plain-secret-value");
        String preview =
                ToolPreviewSupport.buildPreview(
                        ToolNameConstants.CREDENTIAL_MANAGE, args, 1000, true);
        assertFalse(preview.contains("plain-secret-value"));
        assertTrue(preview.contains("***"));
        assertTrue(preview.contains("SSH_PASSWORD"));
    }
}
