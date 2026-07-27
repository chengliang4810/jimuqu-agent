package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.Map;

/** 工具层管理技能维护任务的同步端口。 */
public interface CuratorManagementPort {
    /** 运行技能维护扫描。 */
    Map<String, Object> run(boolean force) throws Exception;

    /** 查询技能维护状态。 */
    Map<String, Object> status();

    /** 暂停技能维护。 */
    Map<String, Object> pause();

    /** 恢复技能维护。 */
    Map<String, Object> resume();

    /** 查询技能维护报告。 */
    Map<String, Object> list(int limit) throws Exception;

    /** 查询单个技能维护报告。 */
    Map<String, Object> detail(String reportId) throws Exception;

    /** 查询技能改进建议。 */
    Map<String, Object> improvements(int limit) throws Exception;

    /** 应用技能改进建议。 */
    Map<String, Object> apply(String skillName, String suggestion);

    /** 忽略技能改进建议。 */
    Map<String, Object> ignore(String skillName, String suggestion);
}
