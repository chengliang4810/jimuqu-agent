package com.jimuqu.claw.bootstrap;

import org.noear.solon.Solon;
import org.noear.solon.annotation.Import;
import org.noear.solon.annotation.SolonMain;

@SolonMain
@Import(scanPackages = "com.jimuqu.claw")
public class ClawApp {
    public static void main(String[] args) {
        Solon.start(ClawApp.class, args);
    }
}
