package com.jimuqu.agent.llm;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.agent.support.constants.LlmConstants;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Provider key / dialect / URL 拼装辅助工具。
 */
public final class LlmProviderSupport {
    private static final Set<String> DIALECTS = new LinkedHashSet<String>(LlmConstants.SUPPORTED_PROVIDERS);

    private LlmProviderSupport() {
    }

    public static boolean isSupportedDialect(String dialect) {
        return DIALECTS.contains(normalizeDialect(dialect));
    }

    public static String normalizeDialect(String dialect) {
        return StrUtil.nullToEmpty(dialect).trim().toLowerCase();
    }

    public static String buildApiUrl(String baseUrl, String dialect) {
        String raw = StrUtil.nullToEmpty(baseUrl).trim();
        if (raw.length() == 0) {
            return "";
        }

        if (raw.endsWith("#")) {
            return raw.substring(0, raw.length() - 1).trim();
        }

        String normalized = stripTrailingSlash(raw);
        String normalizedDialect = normalizeDialect(dialect);
        if (LlmConstants.PROVIDER_OPENAI.equals(normalizedDialect)) {
            return normalized + "/v1/chat/completions";
        }
        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(normalizedDialect)) {
            return normalized + "/v1/responses";
        }
        if (LlmConstants.PROVIDER_OLLAMA.equals(normalizedDialect)) {
            return normalized + "/api/chat";
        }
        if (LlmConstants.PROVIDER_GEMINI.equals(normalizedDialect)) {
            return normalized + "/v1beta";
        }
        if (LlmConstants.PROVIDER_ANTHROPIC.equals(normalizedDialect)) {
            return normalized + "/v1/messages";
        }
        return normalized;
    }

    public static String deriveBaseUrl(String apiUrl, String dialect) {
        String raw = StrUtil.nullToEmpty(apiUrl).trim();
        String normalizedDialect = normalizeDialect(dialect);
        if (raw.length() == 0) {
            return "";
        }

        if (LlmConstants.PROVIDER_OPENAI.equals(normalizedDialect) && StrUtil.endWithIgnoreCase(raw, "/v1/chat/completions")) {
            return raw.substring(0, raw.length() - "/v1/chat/completions".length());
        }
        if (LlmConstants.PROVIDER_OPENAI_RESPONSES.equals(normalizedDialect) && StrUtil.endWithIgnoreCase(raw, "/v1/responses")) {
            return raw.substring(0, raw.length() - "/v1/responses".length());
        }
        if (LlmConstants.PROVIDER_OLLAMA.equals(normalizedDialect) && StrUtil.endWithIgnoreCase(raw, "/api/chat")) {
            return raw.substring(0, raw.length() - "/api/chat".length());
        }
        if (LlmConstants.PROVIDER_GEMINI.equals(normalizedDialect) && StrUtil.endWithIgnoreCase(raw, "/v1beta")) {
            return raw.substring(0, raw.length() - "/v1beta".length());
        }
        if (LlmConstants.PROVIDER_ANTHROPIC.equals(normalizedDialect) && StrUtil.endWithIgnoreCase(raw, "/v1/messages")) {
            return raw.substring(0, raw.length() - "/v1/messages".length());
        }

        return raw + "#";
    }

    public static String stripTrailingSlash(String value) {
        String current = StrUtil.nullToEmpty(value).trim();
        while (current.endsWith("/")) {
            current = current.substring(0, current.length() - 1);
        }
        return current;
    }
}
