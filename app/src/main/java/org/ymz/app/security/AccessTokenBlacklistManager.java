package org.ymz.app.security;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Access Token 黑名单管理组件
 * 负责将已撤销的 access token 标识写入 Redis，并判断其是否已被撤销
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AccessTokenBlacklistManager {

    private static final String KEY_PREFIX = "auth:blacklist:access:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将指定 tokenId 加入黑名单，并设置过期时间
     */
    public void revoke(String tokenId, Duration ttl) {
        if (StrUtil.isBlank(tokenId) || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + tokenId, "1", ttl);
    }

    /**
     * 判断 tokenId 是否在黑名单中
     */
    public boolean isRevoked(String tokenId) {
        if (StrUtil.isBlank(tokenId)) {
            return false;
        }
        return stringRedisTemplate.hasKey(KEY_PREFIX + tokenId);
    }
}
