package com.jimuqu.claw.support;

import com.jimuqu.claw.agent.runtime.model.ChildRunRecord;
import com.jimuqu.claw.agent.runtime.model.RunRecord;
import com.jimuqu.claw.agent.runtime.model.RunStatus;
import com.jimuqu.claw.agent.runtime.model.ToolCallRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

public class JsonSupportTest {
    @Test
    public void roundTripSupportsInstantEnumsAndNestedBeans() {
        Instant createdAt = Instant.parse("2026-04-13T10:15:30.123456789Z");
        Instant childStartedAt = Instant.parse("2026-04-13T10:16:00.100200300Z");
        Instant childCompletedAt = Instant.parse("2026-04-13T10:16:05.400500600Z");
        Instant toolStartedAt = Instant.parse("2026-04-13T10:16:10.700800900Z");
        Instant toolCompletedAt = Instant.parse("2026-04-13T10:16:15.010203040Z");

        RunRecord record = RunRecord.builder()
                .runId("run-1")
                .sessionId("session-1")
                .status(RunStatus.SUCCEEDED)
                .createdAt(createdAt)
                .children(Collections.singletonList(
                        ChildRunRecord.builder()
                                .runId("child-1")
                                .parentRunId("run-1")
                                .status(RunStatus.SUCCEEDED)
                                .summary("child completed")
                                .startedAt(childStartedAt)
                                .completedAt(childCompletedAt)
                                .build()))
                .toolCalls(Collections.singletonList(
                        ToolCallRecord.builder()
                                .runId("run-1")
                                .toolName("todo")
                                .argumentsJson("{\"todos\":[]}")
                                .resultPreview("ok")
                                .success(Boolean.TRUE)
                                .startedAt(toolStartedAt)
                                .completedAt(toolCompletedAt)
                                .build()))
                .build();

        String json = JsonSupport.toJson(record);
        Assertions.assertTrue(json.contains("\"createdAt\":\"2026-04-13T10:15:30.123456789Z\""));
        Assertions.assertTrue(json.contains("\"status\":\"SUCCEEDED\""));

        RunRecord restored = JsonSupport.fromJson(json, RunRecord.class);
        Assertions.assertEquals(createdAt, restored.getCreatedAt());
        Assertions.assertEquals(RunStatus.SUCCEEDED, restored.getStatus());
        Assertions.assertEquals(1, restored.getChildren().size());
        Assertions.assertEquals(childStartedAt, restored.getChildren().get(0).getStartedAt());
        Assertions.assertEquals(childCompletedAt, restored.getChildren().get(0).getCompletedAt());
        Assertions.assertEquals(1, restored.getToolCalls().size());
        Assertions.assertEquals(toolStartedAt, restored.getToolCalls().get(0).getStartedAt());
        Assertions.assertEquals(toolCompletedAt, restored.getToolCalls().get(0).getCompletedAt());
    }
}
