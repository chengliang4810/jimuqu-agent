package com.jimuqu.solon.claw.tool.runtime.port;

import java.util.List;

/** 工具层查询结构化日志的同步端口。 */
public interface LogsQueryPort {
    /** 查询匹配条件的日志行。 */
    List<String> read(String file, int lines, String level, String query, String source);
}
