package com.jimuqu.agent;

import com.jimuqu.agent.support.constants.RuntimePathConstants;
import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

/**
 * 应用启动入口。
 */
@SolonMain
public class JimuquAgentApp {
    private static volatile String[] startupArgs = new String[0];

    /**
     * 启动 Solon 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        startupArgs = args == null ? new String[0] : args.clone();
        System.setProperty("jimuqu.runtime.home", resolveRuntimeHome(args));
        Solon.start(JimuquAgentApp.class, args);
    }

    public static String[] startupArgs() {
        return startupArgs == null ? new String[0] : startupArgs.clone();
    }

    private static String resolveRuntimeHome(String[] args) {
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith("--jimuqu.runtime.home=")) {
                    return arg.substring("--jimuqu.runtime.home=".length());
                }
            }
        }
        return System.getProperty("jimuqu.runtime.home", RuntimePathConstants.RUNTIME_HOME);
    }
}
