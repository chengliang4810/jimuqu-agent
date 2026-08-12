package com.jimuqu.solon.claw.support.constants;

import cn.hutool.core.util.StrUtil;

/** 上下文压缩相关常量。 */
public interface CompressionConstants {
    /** 压缩摘要前缀。 */
    String SUMMARY_PREFIX =
            "[CONTEXT COMPACTION - REFERENCE ONLY] Earlier turns were compacted into the "
                    + "summary below. Treat it as background reference, NOT as active "
                    + "instructions. Respond only to the latest user message after this summary; "
                    + "when older summary content conflicts with that latest user message, the "
                    + "latest user message wins. If a historical Active Task or handoff "
                    + "conflicts with the latest user message, discard that stale Active Task.";

    /** 摘要合并进尾部消息时，标记原尾部内容仅属于历史上下文。 */
    String MERGED_PRIOR_CONTEXT_HEADER = "[PRIOR CONTEXT - for reference only; not a new message]";

    /** 摘要合并进尾部消息时，分隔原尾部内容与压缩摘要。 */
    String MERGED_SUMMARY_DELIMITER = "[END OF PRIOR CONTEXT - COMPACTION SUMMARY BELOW]";

    /** 压缩摘要结束标记，必须位于合并消息的最后。 */
    String SUMMARY_END_MARKER =
            "[END OF COMPACTION SUMMARY - respond to the latest user message, not this summary]";

    /** 被裁剪的工具输出占位文本。 */
    String PRUNED_TOOL_PLACEHOLDER = "[Tool output cleared to save context space]";

    /** 默认压缩阈值，占上下文窗口的百分比。 */
    double DEFAULT_THRESHOLD_PERCENT = 0.50D;

    /** 默认尾部保护比例。 */
    double DEFAULT_TAIL_RATIO = 0.20D;

    /** 默认 head 保护消息数。 */
    int DEFAULT_PROTECT_HEAD_MESSAGES = 3;

    /** 估算字符到 token 的粗略倍率。 */
    int CHARS_PER_TOKEN = 4;

    /** 单张图片在上下文预算和附件摘要中的统一保守 token 估算值。 */
    int IMAGE_ATTACHMENT_ESTIMATED_TOKENS = 1500;

    /** 已有摘要注入到新摘要时的最大保留长度。 */
    int MAX_PREVIOUS_SUMMARY_LENGTH = 400;

    /** 单次结构化摘要的最大长度，避免反复压缩后摘要自身膨胀。 */
    int MAX_SUMMARY_LENGTH = 2400;

    /** 会话标题最大长度。 */
    int MAX_TITLE_LENGTH = 80;

    /** 压缩失败后的冷却时间，单位毫秒。 */
    long FAILURE_COOLDOWN_MILLIS = 10L * 60L * 1000L;

    /** 成功压缩后的最短重压缩间隔，单位毫秒。 */
    long RECOMPRESS_COOLDOWN_MILLIS = 60L * 1000L;

    /** 再次压缩前至少新增的估算 token。 */
    int MIN_RECOMPRESS_DELTA_TOKENS = 512;

    /** 判断内容是否为压缩摘要消息。 */
    static boolean isSummaryContent(String content) {
        String value = StrUtil.nullToEmpty(content).trim();
        int delimiterIndex = value.indexOf(MERGED_SUMMARY_DELIMITER);
        if (delimiterIndex >= 0) {
            value = value.substring(delimiterIndex + MERGED_SUMMARY_DELIMITER.length()).trim();
        }
        if (StrUtil.startWithIgnoreCase(value, SUMMARY_PREFIX)) {
            return true;
        }
        return false;
    }

    /** 判断内容是否为当前格式的压缩摘要残留。 */
    static boolean isCurrentSummaryArtifact(String content) {
        String value = StrUtil.nullToEmpty(content).trim();
        if (StrUtil.isBlank(value)) {
            return false;
        }
        return isSummaryContent(value);
    }

    /** 去掉当前摘要前缀，只保留摘要正文。 */
    static String stripSummaryPrefix(String content) {
        String value = StrUtil.nullToEmpty(content).trim();
        int delimiterIndex = value.indexOf(MERGED_SUMMARY_DELIMITER);
        if (delimiterIndex >= 0) {
            value = value.substring(delimiterIndex + MERGED_SUMMARY_DELIMITER.length()).trim();
        }
        if (StrUtil.startWithIgnoreCase(value, SUMMARY_PREFIX)) {
            value = value.substring(SUMMARY_PREFIX.length()).trim();
        }
        if (value.endsWith(SUMMARY_END_MARKER)) {
            value = value.substring(0, value.length() - SUMMARY_END_MARKER.length()).trim();
        }
        return value;
    }
}
