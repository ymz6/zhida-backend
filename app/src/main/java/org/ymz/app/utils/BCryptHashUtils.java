package org.ymz.app.utils;

import cn.hutool.crypto.digest.BCrypt;

/**
 * BCrypt 哈希加密工具类
 * @author ymz
 */
public class BCryptHashUtils {

    /**
     * 对原始字符串进行 BCrypt 哈希
     */
    public static String hash(String rawValue) {
        return BCrypt.hashpw(rawValue, BCrypt.gensalt());
    }

    /**
     * 校验原始字符串与 BCrypt 哈希值是否匹配
     */
    public static boolean matches(String rawValue, String hashedValue) {
        return BCrypt.checkpw(rawValue, hashedValue);
    }
}