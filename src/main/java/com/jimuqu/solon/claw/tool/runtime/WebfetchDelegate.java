package com.jimuqu.solon.claw.tool.runtime;

/** 定义网页抓取后端契约，避免 Web 工具依赖包含 MCP 客户端的外部 Talent 模块。 */
@FunctionalInterface
public interface WebfetchDelegate {
    /**
     * 获取指定公开网页并转换为请求格式。
     *
     * @param url 待获取的 HTTP(S) 地址。
     * @param format 返回格式，支持 markdown、text 或 html。
     * @param timeoutSeconds 超时秒数。
     * @return 转换后的网页正文。
     * @throws Exception 网络或内容处理失败时抛出异常。
     */
    String webfetch(String url, String format, Integer timeoutSeconds) throws Exception;
}
