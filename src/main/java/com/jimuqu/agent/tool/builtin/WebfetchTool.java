package com.jimuqu.agent.tool.builtin;

import com.jimuqu.agent.support.constants.ToolNameConstants;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.jsoup.Jsoup;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.rag.Document;
import org.noear.solon.annotation.Param;
import org.noear.solon.net.http.HttpResponse;
import org.noear.solon.net.http.HttpUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 网页内容抓取工具。
 */
public class WebfetchTool {
    /**
     * 默认超时时间，单位毫秒。
     */
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    /**
     * 最大超时时间，单位毫秒。
     */
    private static final int MAX_TIMEOUT_MS = 120000;

    /**
     * 最大响应体大小，单位字节。
     */
    private static final long MAX_RESPONSE_SIZE = 5L * 1024L * 1024L;

    /**
     * 单例实例。
     */
    private static final WebfetchTool INSTANCE = new WebfetchTool();

    /**
     * 获取单例实例。
     */
    public static WebfetchTool getInstance() {
        return INSTANCE;
    }

    /**
     * 抓取网页内容。
     */
    @ToolMapping(name = ToolNameConstants.WEBFETCH, description = "从 URL 抓取网页内容并返回 markdown、text 或 html")
    public Document webfetch(
            @Param(name = "url", description = "完整 URL") String url,
            @Param(name = "format", required = false, defaultValue = "markdown", description = "markdown/text/html") String format,
            @Param(name = "timeout", required = false, description = "超时时间，单位秒") Integer timeoutSeconds
    ) throws Exception {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }

        int timeout = timeoutSeconds == null
                ? DEFAULT_TIMEOUT_MS
                : Math.min(timeoutSeconds.intValue() * 1000, MAX_TIMEOUT_MS);
        String finalFormat = format == null ? "markdown" : format.toLowerCase();

        HttpUtils http = HttpUtils.http(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", getAcceptHeader(finalFormat))
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
                .timeout(timeout);

        HttpResponse response = http.exec("GET");
        if (response.code() >= 400) {
            throw new RuntimeException("Request failed with status code: " + response.code());
        }

        long contentLength = response.contentLength();
        if (contentLength > MAX_RESPONSE_SIZE) {
            throw new RuntimeException("Response too large (exceeds 5MB limit)");
        }

        byte[] bodyBytes = response.bodyAsBytes();
        if (bodyBytes == null || bodyBytes.length > MAX_RESPONSE_SIZE) {
            throw new RuntimeException("Response too large (exceeds 5MB limit)");
        }

        String contentType = response.header("Content-Type");
        if (contentType == null) {
            contentType = "";
        }
        String mime = contentType.split(";")[0].trim().toLowerCase();
        String title = url + " (" + contentType + ")";

        if (mime.startsWith("image/") && !mime.contains("svg")) {
            String base64 = Base64.getEncoder().encodeToString(bodyBytes);
            return new Document()
                    .title(title)
                    .content("Image fetched successfully")
                    .metadata("type", "file")
                    .metadata("mime", mime)
                    .metadata("url", "data:" + mime + ";base64," + base64);
        }

        String rawContent = new String(bodyBytes, StandardCharsets.UTF_8);
        boolean isHtml = contentType.contains("text/html");
        String output;
        if ("markdown".equals(finalFormat) && isHtml) {
            output = FlexmarkHtmlConverter.builder().build().convert(rawContent);
        } else if ("text".equals(finalFormat) && isHtml) {
            org.jsoup.nodes.Document document = Jsoup.parse(rawContent);
            document.select("script, style, noscript, iframe, object, embed").remove();
            output = document.text().trim();
        } else {
            output = rawContent;
        }

        return new Document()
                .title(title)
                .content(output)
                .metadata("url", url)
                .metadata("contentType", contentType);
    }

    /**
     * 根据目标格式生成 Accept 头。
     */
    private String getAcceptHeader(String format) {
        if ("markdown".equals(format)) {
            return "text/markdown;q=1.0, text/plain;q=0.8, text/html;q=0.7, */*;q=0.1";
        }
        if ("text".equals(format)) {
            return "text/plain;q=1.0, text/markdown;q=0.9, text/html;q=0.8, */*;q=0.1";
        }
        if ("html".equals(format)) {
            return "text/html;q=1.0, application/xhtml+xml;q=0.9, */*;q=0.1";
        }
        return "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    }
}
