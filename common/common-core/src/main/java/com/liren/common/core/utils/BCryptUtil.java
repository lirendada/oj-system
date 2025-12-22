package com.liren.common.core.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptUtil {
    /**
     * 生成加密后的密文
     */
    public static String encode(String content) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode(content);
    }

    /**
     * 验证内容是否与密文匹配
     */
    public static boolean isMatch(String content, String encoded) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.matches(content, encoded.trim());
    }
}
