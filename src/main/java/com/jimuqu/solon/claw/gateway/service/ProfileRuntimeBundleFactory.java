package com.jimuqu.solon.claw.gateway.service;

import com.jimuqu.solon.claw.config.AppConfig;
import java.nio.file.Path;
import java.util.Map;

/** 创建命名 Profile 子运行时的端口，由应用装配层提供具体实现。 */
public interface ProfileRuntimeBundleFactory {
    /**
     * 创建一个完全独立的命名 Profile 运行时。
     *
     * @param profile 命名 Profile。
     * @param home Profile 工作区。
     * @param environment Profile 局部环境变量。
     * @param appConfig Profile 独立配置。
     * @return 已完成装配的 Profile 子运行时。
     */
    ProfileRuntimeBundle create(
            String profile, Path home, Map<String, String> environment, AppConfig appConfig);
}
