package top.whyh.agentai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.whyh.agentai.cache.RedisCache;
import top.whyh.agentai.cache.RedisKeys;

import java.util.concurrent.TimeUnit;

/**
 * Token 管理服务：负责 Token 的 Redis 存储、验证、删除
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedisCache redisCache;

    /** Token 过期时间（24小时，与 JWT 过期时间一致） */
    private static final long TOKEN_EXPIRE_HOURS = 24;

    /**
     * 保存 Token 到 Redis
     * @param userId 用户ID
     * @param token JWT Token
     */
    public void saveToken(String userId, String token) {
        String key = RedisKeys.getUserTokenKey(userId);
        redisCache.set(key, token, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
        log.info("Token 已存入 Redis | userId: {}", userId);
    }

    /**
     * 验证 Token 是否有效（存在于 Redis 中）
     * @param userId 用户ID
     * @param token JWT Token
     * @return true=有效，false=已失效
     */
    public boolean validateToken(String userId, String token) {
        String key = RedisKeys.getUserTokenKey(userId);
        String cachedToken = (String) redisCache.get(key);
        return token.equals(cachedToken);
    }

    /**
     * 删除 Token（登出、修改密码时调用）
     * @param userId 用户ID
     */
    public void deleteToken(String userId) {
        String key = RedisKeys.getUserTokenKey(userId);
        redisCache.delete(key);
        log.info("Token 已从 Redis 删除 | userId: {}", userId);
    }

    /**
     * 刷新 Token 过期时间（可选，用于"记住我"功能）
     * @param userId 用户ID
     */
    public void refreshTokenExpire(String userId) {
        String key = RedisKeys.getUserTokenKey(userId);
        redisCache.expire(key, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
    }
}
