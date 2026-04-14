package com.jimuqu.claw.channel;

import cn.hutool.core.util.StrUtil;
import com.jimuqu.claw.support.JsonSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChannelPayloadSupport {
    private ChannelPayloadSupport() {
    }

    public static Map<String, Object> parseJsonObject(String body) {
        if (StrUtil.isBlank(body)) {
            return new LinkedHashMap<String, Object>();
        }

        Object raw = JsonSupport.fromJson(body, Object.class);
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }

        return normalizeMap((Map<?, ?>) raw);
    }

    public static Object value(Map<String, Object> payload, String... paths) {
        if (payload == null || paths == null) {
            return null;
        }

        for (String path : paths) {
            Object current = byPath(payload, path);
            if (current != null) {
                return current;
            }
        }

        return null;
    }

    public static String string(Map<String, Object> payload, String... paths) {
        Object value = value(payload, paths);
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            return StrUtil.emptyToNull(text);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return null;
    }

    public static Map<String, Object> map(Map<String, Object> payload, String... paths) {
        Object value = value(payload, paths);
        if (value instanceof Map) {
            return normalizeMap((Map<?, ?>) value);
        }
        return null;
    }

    public static List<String> stringList(Map<String, Object> payload, String... paths) {
        Object value = value(payload, paths);
        if (value == null) {
            return new ArrayList<String>();
        }

        List<String> results = new ArrayList<String>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                String text = item == null ? null : StrUtil.emptyToNull(String.valueOf(item).trim());
                if (text != null) {
                    results.add(text);
                }
            }
            return results;
        }

        String text = StrUtil.emptyToNull(String.valueOf(value).trim());
        if (text == null) {
            return results;
        }

        for (String item : text.split(",")) {
            String normalized = StrUtil.emptyToNull(item.trim());
            if (normalized != null) {
                results.add(normalized);
            }
        }
        return results;
    }

    public static String textValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence) {
            String text = value.toString().trim();
            if (StrUtil.isBlank(text)) {
                return null;
            }
            if (text.startsWith("{") && text.endsWith("}")) {
                try {
                    Map<String, Object> nested = parseJsonObject(text);
                    String nestedText = string(nested, "text", "content.text");
                    if (StrUtil.isNotBlank(nestedText)) {
                        return nestedText;
                    }
                } catch (RuntimeException ignored) {
                    return text;
                }
            }
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map) {
            Map<String, Object> nested = normalizeMap((Map<?, ?>) value);
            return string(nested, "text", "content.text");
        }
        return null;
    }

    private static Object byPath(Map<String, Object> payload, String path) {
        if (payload == null || StrUtil.isBlank(path)) {
            return null;
        }

        Object current = payload;
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (!(current instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> currentMap = (Map<String, Object>) current;
            current = currentMap.get(segment);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> target = new LinkedHashMap<String, Object>(source.size());
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            target.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
        }
        return target;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map) {
            return normalizeMap((Map<?, ?>) value);
        }
        if (value instanceof Collection) {
            List<Object> normalized = new ArrayList<Object>();
            for (Object item : (Collection<?>) value) {
                normalized.add(normalizeValue(item));
            }
            return normalized;
        }
        return value;
    }
}
