package com.jimuqu.agent;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

/**
 * 应用启动入口。
 */
@SolonMain
public class JimuquAgentApp {
    /**
     * 启动 Solon 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        Solon.start(JimuquAgentApp.class, args);
    }
}
