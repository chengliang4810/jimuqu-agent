package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.config.RuntimeConfigResolver;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RuntimeConfigResolverTest {
    @Test
    void shouldReadNestedValuesWithCfgGetAndPreserveTypes() throws Exception {
        File runtimeHome = Files.createTempDirectory("jimuqu-runtime-config").toFile();
        FileUtil.writeUtf8String(
                "jimuqu:\n"
                        + "  display:\n"
                        + "    runtimeFooter:\n"
                        + "      enabled: true\n"
                        + "      fields:\n"
                        + "        - model\n"
                        + "        - cwd\n"
                        + "  skills:\n"
                        + "    curator:\n"
                        + "      intervalHours: 12\n",
                new File(runtimeHome, "config.yml")
        );

        RuntimeConfigResolver.initialize(runtimeHome.getAbsolutePath());

        assertThat(RuntimeConfigResolver.cfgGet("jimuqu.display.runtimeFooter.enabled", false)).isEqualTo(Boolean.TRUE);
        assertThat(RuntimeConfigResolver.cfgGet("jimuqu.skills.curator.intervalHours", 0)).isEqualTo(12);
        assertThat(RuntimeConfigResolver.cfgGet("missing.path", "fallback")).isEqualTo("fallback");
        assertThat(RuntimeConfigResolver.getRawValue("JIMUQU_DISPLAY_RUNTIME_FOOTER_FIELDS"))
                .isEqualTo(java.util.Arrays.asList("model", "cwd"));
    }
}
