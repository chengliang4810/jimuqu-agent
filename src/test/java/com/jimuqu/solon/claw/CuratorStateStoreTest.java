package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.solon.claw.context.CuratorStateStore;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/** 验证技能整理状态损坏时的只读降级和写入保护。 */
public class CuratorStateStoreTest {

    /** 读取可返回空视图，但读改写不得用空状态覆盖损坏文件。 */
    @Test
    void shouldPreserveCorruptedStateOnUpdate() throws Exception {
        File stateFile =
                Files.createTempDirectory("curator-corrupted-state")
                        .resolve(".curator_state")
                        .toFile();
        String corrupted = "{\"skills\":";
        FileUtil.writeUtf8String(corrupted, stateFile);
        CuratorStateStore store = new CuratorStateStore(stateFile);

        assertThat(store.read()).isEmpty();
        assertThatThrownBy(
                        () ->
                                store.update(
                                        state -> {
                                            state.put("mustNotPersist", Boolean.TRUE);
                                            return null;
                                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to overwrite");
        assertThat(FileUtil.readUtf8String(stateFile)).isEqualTo(corrupted);
    }
}
