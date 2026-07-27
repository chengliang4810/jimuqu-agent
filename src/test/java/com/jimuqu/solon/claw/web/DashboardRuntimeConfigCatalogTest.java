package com.jimuqu.solon.claw.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jimuqu.solon.claw.support.ModelConfigKeySupport;
import com.jimuqu.solon.claw.web.DashboardRuntimeConfigCatalog.ConfigItemDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Dashboard 工作区配置目录边界测试。 */
class DashboardRuntimeConfigCatalogTest {
    /** 配置目录必须保留原有 60 项、固定顺序和唯一键。 */
    @Test
    void shouldPreserveDefinitionCountOrderAndUniqueness() {
        DashboardRuntimeConfigCatalog catalog = new DashboardRuntimeConfigCatalog();

        assertThat(catalog.definitions()).hasSize(60);
        assertThat(catalog.keys()).hasSize(60);
        assertThat(new LinkedHashSet<String>(catalog.keys())).hasSize(60);
        assertThat(catalog.keys().get(0)).isEqualTo("solonclaw.react.maxSteps");
        assertThat(catalog.keys().get(catalog.keys().size() - 1))
                .isEqualTo("solonclaw.integrations.github.installationId");
        assertThat(catalog.require("solonclaw.channels.feishu.appId").description)
                .isEqualTo("飞书应用 ID");
        assertThat(catalog.require("solonclaw.update.httpProxy").tools).containsExactly("version");
    }

    /** 只有原有 11 个配置键可以进入密钥更新与明文读取流程。 */
    @Test
    void shouldPreserveSecretClassification() {
        DashboardRuntimeConfigCatalog catalog = new DashboardRuntimeConfigCatalog();
        List<String> secretKeys = new ArrayList<String>();
        for (ConfigItemDefinition definition : catalog.definitions()) {
            if (definition.password) {
                secretKeys.add(definition.key);
            }
        }

        assertThat(secretKeys)
                .containsExactlyElementsOf(
                        Arrays.asList(
                                "solonclaw.channels.feishu.appSecret",
                                "solonclaw.channels.dingtalk.clientSecret",
                                "solonclaw.channels.dingtalk.robotCode",
                                "solonclaw.channels.wecom.secret",
                                "solonclaw.channels.weixin.token",
                                "solonclaw.channels.qqbot.clientSecret",
                                "solonclaw.channels.yuanbao.appSecret",
                                "solonclaw.gateway.injectionSecret",
                                "solonclaw.dashboard.accessToken",
                                "solonclaw.integrations.github.token",
                                "solonclaw.integrations.github.cliToken"));
    }

    /** 模型专属键和未知键必须继续返回原有两类错误。 */
    @Test
    void shouldPreserveUnsupportedKeyErrors() {
        DashboardRuntimeConfigCatalog catalog = new DashboardRuntimeConfigCatalog();

        assertThatThrownBy(() -> catalog.require("providers.default.apiKey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ModelConfigKeySupport.DEDICATED_ENTRY_MESSAGE);
        assertThatThrownBy(() -> catalog.require("solonclaw.unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported workspace config item");
    }
}
