package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层管理命名 Profile 的同步端口。 */
public interface ProfileManagementPort {
    /** 查询全部 Profile。 */
    Map<String, Object> listProfiles() throws Exception;

    /** 查询指定 Profile。 */
    Map<String, Object> showProfile(String name) throws Exception;

    /** 更新 Profile 模型。 */
    Map<String, Object> updateModel(String name, String provider, String model) throws Exception;

    /** 更新 Profile 职责描述。 */
    Map<String, Object> updateDescription(String name, String description) throws Exception;

    /** 重命名 Profile。 */
    Map<String, Object> renameProfile(String name, String newName) throws Exception;

    /** 删除 Profile。 */
    Map<String, Object> deleteProfile(String name) throws Exception;
}
