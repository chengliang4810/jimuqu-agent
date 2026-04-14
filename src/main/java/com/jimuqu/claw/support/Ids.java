package com.jimuqu.claw.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;

public final class Ids {
    private Ids() {
    }

    public static String runId() {
        return "run_" + IdUtil.fastSimpleUUID();
    }

    public static String jobId() {
        return "job_" + IdUtil.fastSimpleUUID();
    }

    public static String processId() {
        return "proc_" + IdUtil.fastSimpleUUID();
    }

    public static String childRunId() {
        return "child_" + IdUtil.fastSimpleUUID();
    }

    public static String sessionId(String platform, String chatId, String threadId, String userId) {
        String raw = safe(platform) + "|" + safe(chatId) + "|" + safe(threadId) + "|" + safe(userId);
        return "sess_" + SecureUtil.md5(raw);
    }

    public static String hashKey(String key) {
        return SecureUtil.md5(key);
    }

    private static String safe(String value) {
        return value == null ? "-" : value;
    }
}
