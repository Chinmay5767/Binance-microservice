package com.example.binanceAuth.binance_auth.service;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String USER_CACHE_KEY = "user:cache:";
    private static final String USER_ROLES_KEY = "user:roles:";

    /**
     * ✅ Cache user data in Redis (Shared across all microservices)
     */
    public void cacheUser(AuthUserEntity user) {
        try {
            String key = USER_CACHE_KEY + user.getUserId();
            redisTemplate.opsForValue().set(key, user, 30, TimeUnit.MINUTES);
            
            // Cache user roles separately for quick access
            String rolesKey = USER_ROLES_KEY + user.getUserId();
            redisTemplate.opsForValue().set(rolesKey, user.getRoles(), 30, TimeUnit.MINUTES);
            
            log.debug("User cached in Redis: {}", user.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to cache user: {}", e.getMessage());
        }
    }

    /**
     * ✅ Get cached user (Available to ALL microservices)
     */
    public AuthUserEntity getCachedUser(Long userId) {
        try {
            String key = USER_CACHE_KEY + userId;
            return (AuthUserEntity) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Failed to get cached user: {}", e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Get cached user roles
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> getCachedUserRoles(Long userId) {
        try {
            String key = USER_ROLES_KEY + userId;
            return (java.util.List<String>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Failed to get cached user roles: {}", e.getMessage());
            return null;
        }
    }

    /**
     * ✅ Invalidate user cache (when user data changes)
     */
    public void invalidateUserCache(Long userId) {
        try {
            String key = USER_CACHE_KEY + userId;
            redisTemplate.delete(key);
            
            String rolesKey = USER_ROLES_KEY + userId;
            redisTemplate.delete(rolesKey);
            
            log.debug("User cache invalidated: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to invalidate user cache: {}", e.getMessage());
        }
    }

    /**
     * ✅ Check if user is cached
     */
    public boolean isUserCached(Long userId) {
        try {
            String key = USER_CACHE_KEY + userId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Failed to check user cache: {}", e.getMessage());
            return false;
        }
    }
}