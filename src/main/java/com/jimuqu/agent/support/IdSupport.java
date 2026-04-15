package com.jimuqu.agent.support;

import cn.hutool.core.util.IdUtil;

public final class IdSupport {
    private IdSupport() {
    }

    public static String newId() {
        return IdUtil.fastSimpleUUID();
    }
}
