package com.jimuqu.solon.claw;

import static org.assertj.core.api.Assertions.assertThat;

import com.jimuqu.solon.claw.support.SecureTokenCompare;
import org.junit.jupiter.api.Test;

/** 验证敏感值比较的精确匹配语义。 */
public class SecureTokenCompareTest {
    /** 相同、前后缀、不同长度、空值和 Unicode 必须保持精确字节语义。 */
    @Test
    void shouldCompareSensitiveValuesByExactUtf8Bytes() {
        assertThat(SecureTokenCompare.matches("token-value", "token-value")).isTrue();
        assertThat(SecureTokenCompare.matches("token-value", "token")).isFalse();
        assertThat(SecureTokenCompare.matches("token-value", "token-value-suffix")).isFalse();
        assertThat(SecureTokenCompare.matches("token-value", "Token-value")).isFalse();
        assertThat(SecureTokenCompare.matches("", "")).isFalse();
        assertThat(SecureTokenCompare.matches(null, "token-value")).isFalse();
        assertThat(SecureTokenCompare.matches("token-value", null)).isFalse();
        assertThat(SecureTokenCompare.matches("令牌-β", "令牌-β")).isTrue();
        assertThat(SecureTokenCompare.matches("令牌-β", "令牌-Β")).isFalse();
    }
}
