package com.jimuqu.solon.claw.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 提供认证令牌和签名的常量时间字节比较。 */
public final class SecureTokenCompare {
    /** 禁止创建无状态安全比较工具实例。 */
    private SecureTokenCompare() {}

    /**
     * 按 UTF-8 字节常量时间比较两个非空敏感值。
     *
     * @param expected 服务端期望值。
     * @param actual 外部提交值。
     * @return 两个非空值字节完全一致时返回 true。
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null || expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
