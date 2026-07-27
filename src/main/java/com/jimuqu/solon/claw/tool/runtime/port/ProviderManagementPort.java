package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.List;
import java.util.Map;

/** 工具层管理模型提供方的同步端口。 */
public interface ProviderManagementPort {
    /** 查询提供方列表。 */
    Map<String, Object> listProviders();

    /** 查询推荐模型列表。 */
    Map<String, Object> JimuquModels();

    /** 查询提供方健康状态。 */
    Map<String, Object> health();

    /** 创建提供方。 */
    Map<String, Object> createProvider(Map<String, Object> body);

    /** 更新提供方。 */
    Map<String, Object> updateProvider(String providerKey, Map<String, Object> body);

    /** 删除提供方。 */
    Map<String, Object> deleteProvider(String providerKey);

    /** 更新默认模型。 */
    Map<String, Object> updateDefaultModel(String providerKey, String model);

    /** 更新备用提供方。 */
    Map<String, Object> updateFallbackProviders(List<Map<String, Object>> providers);

    /** 查询远端模型。 */
    Map<String, Object> listRemoteModels(Map<String, Object> body);

    /** 校验提供方配置。 */
    Map<String, Object> validateProvider(Map<String, Object> body);
}
