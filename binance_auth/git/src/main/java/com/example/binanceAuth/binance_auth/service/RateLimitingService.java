package com.example.binanceAuth.binance_auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.security.rate-limiting.default-limit:100}")
    private int defaultLimit;

    @Value("${app.security.rate-limiting.login-limit:10}")
    private int loginLimit;

    @Value("${app.security.rate-limiting.register-limit:5}")
    private int registerLimit;

    @Value("${app.mfa.otp.max-resend:3}")
    private int mfaResendLimit;

    @Value("${app.mfa.otp.max-attempts:5}")
    private int mfaVerifyLimit;

    @Value("${app.security.rate-limiting.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.mfa.otp.resend-window-minutes:15}")
    private long mfaResendWindowMinutes;

    private static final String RATE_LIMIT_KEY = "ratelimit:";

    /**
     * ✅ Check if rate limit is exceeded (Global across all instances)
     */
    public boolean isRateLimited(String key, int limit, long windowSeconds) {
        String rateKey = RATE_LIMIT_KEY + key;
        
        try {
            // Atomic increment in Redis (thread-safe across instances)
            Long count = redisTemplate.opsForValue().increment(rateKey);
            
            if (count == 1) {
                // First request - set expiry
                redisTemplate.expire(rateKey, windowSeconds, TimeUnit.SECONDS);
            }
            
            // Check if exceeded limit
            boolean exceeded = count != null && count > limit;
            
            if (exceeded) {
                log.warn("Rate limit exceeded for key: {}, count: {}, limit: {}", 
                    key, count, limit);
            }
            
            return exceeded;
            
        } catch (Exception e) {
            log.warn("Redis rate limit failed, allowing request: {}", e.getMessage());
            return false; // Fail open
        }
    }

    /**
     * ✅ Check login rate limit (by email + IP)
     */
    public boolean isLoginRateLimited(String email, String ip) {
        String key = "login:" + email + ":" + ip;
        return isRateLimited(key, loginLimit, windowSeconds);
    }

    /**
     * ✅ Check registration rate limit (by email + IP)
     */
    public boolean isRegistrationRateLimited(String email, String ip) {
        String key = "register:" + email + ":" + ip;
        return isRateLimited(key, registerLimit, windowSeconds * 10); // 10 minute window
    }

    /**
     * Check MFA OTP resend rate limit
     */
    public boolean isMfaResendRateLimited(String challengeToken, String ip) {
        String key = "mfa:resend:" + challengeToken + ":" + ip;
        return isRateLimited(key, mfaResendLimit, mfaResendWindowMinutes * 60);
    }

    /**
     * Check MFA OTP verification attempt rate limit
     */
    public boolean isMfaVerifyRateLimited(String challengeToken) {
        String key = "mfa:verify:" + challengeToken;
        return isRateLimited(key, mfaVerifyLimit, mfaResendWindowMinutes * 60);
    }

    /**
     * ✅ Check API rate limit (by user ID)
     */
    public boolean isApiRateLimited(Long userId) {
        String key = "api:user:" + userId;
        return isRateLimited(key, defaultLimit, windowSeconds);
    }

    /**
     * ✅ Check API rate limit (by IP)
     */
    public boolean isApiRateLimitedByIp(String ip) {
        String key = "api:ip:" + ip;
        return isRateLimited(key, defaultLimit * 2, windowSeconds);
    }

    /**
     * ✅ Get remaining requests for a key
     */
    public long getRemainingRequests(String key, int limit) {
        try {
            String rateKey = RATE_LIMIT_KEY + key;
            String countStr = redisTemplate.opsForValue().get(rateKey);
            if (countStr == null) {
                return limit;
            }
            long count = Long.parseLong(countStr);
            return Math.max(0, limit - count);
        } catch (Exception e) {
            log.warn("Failed to get remaining requests: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * ✅ Reset rate limit for a key
     */
    public void resetRateLimit(String key) {
        try {
            String rateKey = RATE_LIMIT_KEY + key;
            redisTemplate.delete(rateKey);
        } catch (Exception e) {
            log.warn("Failed to reset rate limit: {}", e.getMessage());
        }
    }

    /**
     * ✅ Get rate limit status for a key
     */
    public RateLimitStatus getRateLimitStatus(String key, int limit) {
        try {
            String rateKey = RATE_LIMIT_KEY + key;
            String countStr = redisTemplate.opsForValue().get(rateKey);
            Long ttl = redisTemplate.getExpire(rateKey, TimeUnit.SECONDS);
            
            long count = countStr != null ? Long.parseLong(countStr) : 0;
            boolean exceeded = count > limit;
            long remaining = Math.max(0, limit - count);
            
            return RateLimitStatus.builder()
                .limit(limit)
                .currentCount(count)
                .remaining(remaining)
                .exceeded(exceeded)
                .resetInSeconds(ttl != null ? ttl : 0)
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to get rate limit status: {}", e.getMessage());
            return RateLimitStatus.builder()
                .limit(limit)
                .currentCount(0)
                .remaining(limit)
                .exceeded(false)
                .resetInSeconds(0)
                .build();
        }
    }

    // Inner class for rate limit status
    @lombok.Builder
    @lombok.Data
    public static class RateLimitStatus {
        private int limit;
        private long currentCount;
        private long remaining;
        private boolean exceeded;
        private long resetInSeconds;
    }
}