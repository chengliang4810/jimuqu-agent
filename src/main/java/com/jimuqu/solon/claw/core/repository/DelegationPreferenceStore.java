package com.jimuqu.solon.claw.core.repository;

/** 子 Agent 委派所需的最小工具偏好存储端口。 */
public interface DelegationPreferenceStore {
    /**
     * 判断指定来源是否启用工具。
     *
     * @param sourceKey 渠道来源键。
     * @param toolName 工具名称。
     * @return 已启用时返回 true。
     * @throws Exception 偏好读取失败。
     */
    boolean isToolEnabled(String sourceKey, String toolName) throws Exception;

    /**
     * 按调用方提供的缺省值判断指定来源是否启用工具。
     *
     * @param sourceKey 渠道来源键。
     * @param toolName 工具名称。
     * @param defaultValue 尚未配置时的缺省值。
     * @return 已启用时返回 true。
     * @throws Exception 偏好读取失败。
     */
    boolean isToolEnabled(String sourceKey, String toolName, boolean defaultValue) throws Exception;

    /**
     * 更新指定来源的工具启用状态。
     *
     * @param sourceKey 渠道来源键。
     * @param toolName 工具名称。
     * @param enabled 启用状态。
     * @throws Exception 偏好写入失败。
     */
    void setToolEnabled(String sourceKey, String toolName, boolean enabled) throws Exception;
}
