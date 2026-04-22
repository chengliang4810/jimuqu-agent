package com.jimuqu.agent.support.constants;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人格工作区上下文文件常量。
 */
public final class ContextFileConstants {
    public static final String KEY_AGENTS = "agents";
    public static final String KEY_SOUL = "soul";
    public static final String KEY_IDENTITY = "identity";
    public static final String KEY_USER = "user";

    public static final String FILE_AGENTS = "AGENTS.md";
    public static final String FILE_SOUL = "SOUL.md";
    public static final String FILE_IDENTITY = "IDENTITY.md";
    public static final String FILE_USER = "USER.md";

    private static final Map<String, String> FILES_BY_KEY;
    private static final List<String> ORDERED_KEYS;

    static {
        LinkedHashMap<String, String> files = new LinkedHashMap<String, String>();
        files.put(KEY_AGENTS, FILE_AGENTS);
        files.put(KEY_SOUL, FILE_SOUL);
        files.put(KEY_IDENTITY, FILE_IDENTITY);
        files.put(KEY_USER, FILE_USER);
        FILES_BY_KEY = Collections.unmodifiableMap(files);
        ORDERED_KEYS = Collections.unmodifiableList(new ArrayList<String>(files.keySet()));
    }

    private ContextFileConstants() {
    }

    /**
     * 返回受控文件 key 顺序。
     */
    public static List<String> orderedKeys() {
        return ORDERED_KEYS;
    }

    /**
     * 判断是否为受控文件 key。
     */
    public static boolean isManagedKey(String key) {
        return FILES_BY_KEY.containsKey(normalizeKey(key));
    }

    /**
     * 解析 key 对应文件名。
     */
    public static String fileName(String key) {
        String normalized = normalizeKey(key);
        String fileName = FILES_BY_KEY.get(normalized);
        if (fileName == null) {
            throw new IllegalArgumentException("Unsupported context file key: " + key);
        }
        return fileName;
    }

    /**
     * 归一化文件 key。
     */
    public static String normalizeKey(String key) {
        return StrUtil.nullToEmpty(key).trim().toLowerCase();
    }
}
