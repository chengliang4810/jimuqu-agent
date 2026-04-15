package com.jimuqu.agent.bootstrap;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

@SolonMain
public class JimuquAgentApp {
    public static void main(String[] args) {
        Solon.start(JimuquAgentApp.class, args);
    }
}
