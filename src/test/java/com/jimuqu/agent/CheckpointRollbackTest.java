package com.jimuqu.agent;

import cn.hutool.core.io.FileUtil;
import com.jimuqu.agent.core.model.SessionRecord;
import com.jimuqu.agent.support.TestEnvironment;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckpointRollbackTest {
    @Test
    void shouldRollbackLatestStructuredFileWrite() throws Exception {
        TestEnvironment env = TestEnvironment.withFakeLlm();
        String sourceKey = "MEMORY:room-a:user-a";
        SessionRecord session = env.sessionRepository.bindNewSession(sourceKey);

        File file = FileUtil.file(env.appConfig.getRuntime().getCacheDir(), "sample.txt");
        FileUtil.writeUtf8String("v1", file);
        env.checkpointService.createCheckpoint(sourceKey, session.getSessionId(), Collections.singletonList(file));
        FileUtil.writeUtf8String("v2", file);

        env.checkpointService.rollbackLatest(sourceKey);
        assertThat(FileUtil.readUtf8String(file)).isEqualTo("v1");
    }
}
