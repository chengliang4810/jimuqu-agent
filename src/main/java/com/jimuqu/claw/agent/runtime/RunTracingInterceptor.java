package com.jimuqu.claw.agent.runtime;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.agent.runtime.model.ToolCallRecord;
import com.jimuqu.claw.support.JsonSupport;
import org.noear.solon.ai.agent.react.ReActInterceptor;
import org.noear.solon.ai.agent.react.ReActTrace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunTracingInterceptor implements ReActInterceptor {
    private static final String TOOL_CALLS_KEY = "toolCalls";

    @Override
    public void onAction(ReActTrace trace, String toolName, Map<String, Object> args) {
        List<ToolCallRecord> toolCalls = getToolCalls(trace);
        toolCalls.add(ToolCallRecord.builder()
                .toolName(toolName)
                .argumentsJson(JsonSupport.toJson(args))
                .startedAt(Instant.now())
                .build());
    }

    @Override
    public void onObservation(ReActTrace trace, String toolName, String result, long durationMs) {
        ToolCallRecord record = latestPendingRecord(trace, toolName);
        if (record == null) {
            return;
        }

        record.setSuccess(Boolean.TRUE);
        record.setCompletedAt(Instant.now());
        record.setResultPreview(StrUtil.maxLength(StrUtil.nullToDefault(result, ""), 2000));
    }

    @Override
    public void onAgentEnd(ReActTrace trace) {
        for (ToolCallRecord record : getToolCalls(trace)) {
            if (record.getCompletedAt() == null) {
                record.setCompletedAt(Instant.now());
                record.setSuccess(Boolean.FALSE);
            }
        }
    }

    public static List<ToolCallRecord> extract(ReActTrace trace, String runId) {
        List<ToolCallRecord> copied = new ArrayList<ToolCallRecord>();
        for (ToolCallRecord record : getToolCalls(trace)) {
            ToolCallRecord cloned = ToolCallRecord.builder()
                    .runId(runId)
                    .toolName(record.getToolName())
                    .argumentsJson(record.getArgumentsJson())
                    .resultPreview(record.getResultPreview())
                    .success(record.getSuccess())
                    .startedAt(record.getStartedAt())
                    .completedAt(record.getCompletedAt())
                    .build();
            copied.add(cloned);
        }

        return copied;
    }

    @SuppressWarnings("unchecked")
    private static List<ToolCallRecord> getToolCalls(ReActTrace trace) {
        List<ToolCallRecord> toolCalls = trace.getExtraAs(TOOL_CALLS_KEY);
        if (toolCalls == null) {
            toolCalls = new ArrayList<ToolCallRecord>();
            trace.setExtra(TOOL_CALLS_KEY, toolCalls);
        }
        return toolCalls;
    }

    private ToolCallRecord latestPendingRecord(ReActTrace trace, String toolName) {
        List<ToolCallRecord> toolCalls = getToolCalls(trace);
        for (int i = toolCalls.size() - 1; i >= 0; i--) {
            ToolCallRecord record = toolCalls.get(i);
            if (record.getCompletedAt() == null && toolName.equals(record.getToolName())) {
                return record;
            }
        }

        return null;
    }
}
