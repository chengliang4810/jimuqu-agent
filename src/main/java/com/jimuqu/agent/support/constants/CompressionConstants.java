package com.jimuqu.agent.support.constants;

/**
 * 上下文压缩相关常量。
 */
public interface CompressionConstants {
    /**
     * 压缩摘要前缀。
     */
    String SUMMARY_PREFIX = "[CONTEXT COMPACTION]";

    /**
     * 被裁剪的旧工具输出占位文本。
     */
    String PRUNED_TOOL_PLACEHOLDER = "[Old tool output cleared to save context space]";

    /**
     * 默认压缩阈值，占上下文窗口的百分比。
     */
    double DEFAULT_THRESHOLD_PERCENT = 0.50D;

    /**
     * 默认尾部保护比例。
     */
    double DEFAULT_TAIL_RATIO = 0.20D;

    /**
     * 默认 head 保护消息数。
     */
    int DEFAULT_PROTECT_HEAD_MESSAGES = 3;

    /**
     * 估算字符到 token 的粗略倍率。
     */
    int CHARS_PER_TOKEN = 4;

    /**
     * 会话标题最大长度。
     */
    int MAX_TITLE_LENGTH = 80;
}
