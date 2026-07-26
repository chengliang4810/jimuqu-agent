package com.jimuqu.solon.claw.support;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.core.model.SessionRecord;
import java.util.Map;
import org.noear.snack4.ONode;

/** 会话用户可见性规则，供 Dashboard 列表与搜索统一复用。 */
public final class SessionVisibilitySupport {
    /** 会话归档标记在扩展元数据中的稳定字段名。 */
    private static final String ARCHIVED_METADATA_KEY = "archived";

    /** 禁止创建无状态辅助类实例。 */
    private SessionVisibilitySupport() {}

    /**
     * 判断会话是否由用户对话发起，而不是心跳、定时任务、ProfileTask 或内部子会话。
     *
     * @param record 待判断的会话记录。
     * @return 普通用户会话或显式分支返回 true。
     */
    public static boolean isUserInitiatedConversation(SessionRecord record) {
        if (record == null || SourceKeySupport.isBackgroundSource(record.getSourceKey())) {
            return false;
        }
        return StrUtil.isBlank(record.getParentSessionId()) || isExplicitBranch(record);
    }

    /**
     * 判断会话是否可出现在默认用户对话列表或对话搜索中。
     *
     * @param record 待判断的会话记录。
     * @return 用户发起且未归档的会话返回 true。
     */
    public static boolean isUserVisibleConversation(SessionRecord record) {
        return isUserInitiatedConversation(record) && !isArchived(record);
    }

    /**
     * 判断父会话下的记录是否为用户显式创建的命名分支。
     *
     * @param record 待判断的会话记录。
     * @return 非空且非 main 的分支返回 true。
     */
    public static boolean isExplicitBranch(SessionRecord record) {
        String branch = record == null ? null : StrUtil.nullToEmpty(record.getBranchName()).trim();
        return StrUtil.isNotBlank(branch) && !"main".equalsIgnoreCase(branch);
    }

    /**
     * 判断会话是否已归档；损坏的历史元数据按未归档处理。
     *
     * @param record 待判断的会话记录。
     * @return 元数据明确标记归档时返回 true。
     */
    @SuppressWarnings("unchecked")
    public static boolean isArchived(SessionRecord record) {
        if (record == null || StrUtil.isBlank(record.getMetadataJson())) {
            return false;
        }
        try {
            Object parsed = ONode.deserialize(record.getMetadataJson(), Object.class);
            if (!(parsed instanceof Map)) {
                return false;
            }
            Object value = ((Map<String, Object>) parsed).get(ARCHIVED_METADATA_KEY);
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
            String normalized = StrUtil.nullToEmpty(String.valueOf(value)).trim();
            return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
        } catch (Exception ignored) {
            return false;
        }
    }
}
