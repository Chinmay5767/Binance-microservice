package com.example.binanceAuth.binance_auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.secret:67d8d84808c94192174972b8c069af2489dcbe398f9f81bbe94547a1fa7f5c35}")
    private String jwtSecret;

    // Redis Keys
    private static final String BLACKLIST_KEY = "blacklist:token:";
    private static final String USER_TOKENS_KEY = "user:tokens:";
    private static final String DEVICE_TOKENS_KEY = "device:tokens:";
    private static final String USER_BLACKLIST_KEY = "user:blacklist:";
    private static final String TOKEN_AUDIT_KEY = "token:audit:";

    // ═══════════════════════════════════════════════════════════════
    // 1. TOKEN BLACKLISTING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Blacklist a token (Shared across all microservices)
     */
    public void blacklistToken(String token, Long userId, String deviceId) {
        try {
            // 1. Add to blacklist
            String blacklistKey = BLACKLIST_KEY + token;
            long ttl = getTokenExpiry(token);
            
            redisTemplate.opsForValue().set(
                blacklistKey,
                "blacklisted",
                ttl > 0 ? ttl : 900,
                TimeUnit.SECONDS
            );

            // 2. Track user tokens
            if (userId != null) {
                String userKey = USER_TOKENS_KEY + userId;
                redisTemplate.opsForSet().add(userKey, token);
                redisTemplate.expire(userKey, 7, TimeUnit.DAYS);
                
                // Track blacklisted tokens per user
                String userBlacklistKey = USER_BLACKLIST_KEY + userId;
                redisTemplate.opsForSet().add(userBlacklistKey, token);
                redisTemplate.expire(userBlacklistKey, 7, TimeUnit.DAYS);
            }

            // 3. Track device tokens
            if (deviceId != null) {
                String deviceKey = DEVICE_TOKENS_KEY + deviceId;
                redisTemplate.opsForSet().add(deviceKey, token);
                redisTemplate.expire(deviceKey, 7, TimeUnit.DAYS);
            }

            // 4. Audit the revocation
            auditTokenRevocation(token, userId, deviceId, "USER_LOGOUT");

            log.info("Token blacklisted in Redis. User: {}, Device: {}", userId, deviceId);

        } catch (Exception e) {
            log.error("Failed to blacklist token in Redis: {}", e.getMessage());
        }
    }

    /**
     * Check if token is blacklisted (Called by ALL microservices)
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            if (token == null || token.isEmpty()) {
                return false;
            }
            String blacklistKey = BLACKLIST_KEY + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
        } catch (Exception e) {
            log.warn("Redis unavailable, allowing token: {}", e.getMessage());
            return false; // Fail open
        }
    }

    /**
     * Get blacklist status for a token
     */
    public BlacklistStatus getBlacklistStatus(String token) {
        if (token == null || token.isEmpty()) {
            return new BlacklistStatus(false, 0);
        }

        String blacklistKey = BLACKLIST_KEY + token;
        Boolean exists = redisTemplate.hasKey(blacklistKey);
        Long ttl = redisTemplate.getExpire(blacklistKey, TimeUnit.SECONDS);

        return new BlacklistStatus(
            Boolean.TRUE.equals(exists),
            ttl != null && ttl > 0 ? ttl : 0
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. MASS REVOCATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Revoke all tokens for a user
     */
    public void revokeAllUserTokens(Long userId) {
        try {
            // 1. Get all user tokens
            String userKey = USER_TOKENS_KEY + userId;
            Set<String> tokens = redisTemplate.opsForSet().members(userKey);

            // 2. Blacklist each token
            if (tokens != null && !tokens.isEmpty()) {
                for (String token : tokens) {
                    String blacklistKey = BLACKLIST_KEY + token;
                    redisTemplate.opsForValue().set(
                        blacklistKey,
                        "revoked",
                        86400,
                        TimeUnit.SECONDS
                    );
                }
            }

            // 3. Clean up
            redisTemplate.delete(userKey);
            String userBlacklistKey = USER_BLACKLIST_KEY + userId;
            redisTemplate.delete(userBlacklistKey);

            // 4. Audit
            auditTokenRevocation(null, userId, null, "ADMIN_REVOKE_ALL");

            log.info("All tokens revoked for user: {}", userId);

        } catch (Exception e) {
            log.error("Failed to revoke user tokens: {}", e.getMessage());
        }
    }

    /**
     * Revoke tokens for a specific device
     */
    public void revokeDeviceTokens(String deviceId) {
        try {
            String deviceKey = DEVICE_TOKENS_KEY + deviceId;
            Set<String> tokens = redisTemplate.opsForSet().members(deviceKey);

            if (tokens != null && !tokens.isEmpty()) {
                for (String token : tokens) {
                    String blacklistKey = BLACKLIST_KEY + token;
                    redisTemplate.opsForValue().set(
                        blacklistKey,
                        "revoked",
                        86400,
                        TimeUnit.SECONDS
                    );
                }
                redisTemplate.delete(deviceKey);
            }

            log.info("All tokens revoked for device: {}", deviceId);

        } catch (Exception e) {
            log.error("Failed to revoke device tokens: {}", e.getMessage());
        }
    }

    /**
     * Revoke tokens issued before a specific timestamp
     */
    public void revokeTokensBefore(Long userId, long timestamp) {
        try {
            String userKey = USER_TOKENS_KEY + userId;
            Set<String> tokens = redisTemplate.opsForSet().members(userKey);

            if (tokens != null && !tokens.isEmpty()) {
                for (String token : tokens) {
                    long issuedAt = extractTokenIssuedAt(token);
                    if (issuedAt < timestamp) {
                        String blacklistKey = BLACKLIST_KEY + token;
                        redisTemplate.opsForValue().set(
                            blacklistKey,
                            "revoked",
                            86400,
                            TimeUnit.SECONDS
                        );
                    }
                }
            }

            log.info("Revoked tokens before {} for user: {}", timestamp, userId);

        } catch (Exception e) {
            log.error("Failed to revoke tokens before timestamp: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. TOKEN AUDIT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Audit token revocation
     */
    private void auditTokenRevocation(String token, Long userId, String deviceId, String reason) {
        try {
            String auditKey = TOKEN_AUDIT_KEY + (userId != null ? userId : "system");
            
            Map<String, Object> auditEntry = new HashMap<>();
            auditEntry.put("tokenHash", token != null ? getTokenHash(token) : null);
            auditEntry.put("userId", userId);
            auditEntry.put("deviceId", deviceId);
            auditEntry.put("reason", reason);
            auditEntry.put("timestamp", System.currentTimeMillis());

            redisTemplate.opsForList().leftPush(auditKey, auditEntry.toString());
            redisTemplate.expire(auditKey, 30, TimeUnit.DAYS);

        } catch (Exception e) {
            log.warn("Failed to audit token revocation: {}", e.getMessage());
        }
    }

    /**
     * Get token audit history for a user
     */
    public List<Map<String, Object>> getTokenAuditHistory(Long userId, int limit) {
        try {
            String auditKey = TOKEN_AUDIT_KEY + userId;
            List<String> entries = redisTemplate.opsForList().range(auditKey, 0, limit - 1);
            
            List<Map<String, Object>> history = new ArrayList<>();
            if (entries != null) {
                for (String entry : entries) {
                    try {
                        // Parse the entry (simple parsing)
                        Map<String, Object> parsed = parseAuditEntry(entry);
                        history.add(parsed);
                    } catch (Exception e) {
                        // Skip malformed entries
                    }
                }
            }
            return history;

        } catch (Exception e) {
            log.error("Failed to get token audit history: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get all blacklisted tokens for a user
     */
    public Set<String> getUserBlacklistedTokens(Long userId) {
        try {
            String userBlacklistKey = USER_BLACKLIST_KEY + userId;
            return redisTemplate.opsForSet().members(userBlacklistKey);
        } catch (Exception e) {
            log.error("Failed to get user blacklisted tokens: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. STATISTICS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get blacklist statistics
     */
    public BlacklistStats getBlacklistStats() {
        BlacklistStats stats = new BlacklistStats();
        try {
            // Get total blacklisted tokens
            Set<String> blacklistKeys = redisTemplate.keys(BLACKLIST_KEY + "*");
            stats.setTotalBlacklisted(blacklistKeys != null ? blacklistKeys.size() : 0);

            // Get total users with tokens
            Set<String> userKeys = redisTemplate.keys(USER_TOKENS_KEY + "*");
            stats.setTotalUsersWithTokens(userKeys != null ? userKeys.size() : 0);

            // Get total devices with tokens
            Set<String> deviceKeys = redisTemplate.keys(DEVICE_TOKENS_KEY + "*");
            stats.setTotalDevicesWithTokens(deviceKeys != null ? deviceKeys.size() : 0);

            // Get total revoked tokens in last hour
            long oneHourAgo = System.currentTimeMillis() - 3600000;
            // This would require additional tracking

        } catch (Exception e) {
            log.error("Failed to get blacklist stats: {}", e.getMessage());
        }
        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get token expiry from JWT
     */
    private long getTokenExpiry(String token) {
        try {
            Claims claims = extractClaims(token);
            return (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
        } catch (Exception e) {
            // If can't parse, default to 900 seconds (15 minutes)
            return 900;
        }
    }

    /**
     * Extract token issued at timestamp
     */
    private long extractTokenIssuedAt(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getIssuedAt().getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * Extract claims from JWT
     */
    private Claims extractClaims(String token) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
        	    .verifyWith((SecretKey) key)
        	    .build()
        	    .parseSignedClaims(token)
        	    .getPayload();   
    }

    /**
     * Generate token hash for indexing
     */
    private String getTokenHash(String token) {
        return Integer.toHexString(token.hashCode());
    }

    /**
     * Parse audit entry
     */
    private Map<String, Object> parseAuditEntry(String entry) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Simple parsing - in production, use JSON
            String[] parts = entry.replace("{", "").replace("}", "").split(", ");
            for (String part : parts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    result.put(kv[0], kv[1]);
                }
            }
        } catch (Exception e) {
            // Skip malformed entries
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. INNER CLASSES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Blacklist status
     */
    public static class BlacklistStatus {
        private final boolean blacklisted;
        private final long ttlSeconds;

        public BlacklistStatus(boolean blacklisted, long ttlSeconds) {
            this.blacklisted = blacklisted;
            this.ttlSeconds = ttlSeconds;
        }

        public boolean isBlacklisted() {
            return blacklisted;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public String getTtlFormatted() {
            long hours = ttlSeconds / 3600;
            long minutes = (ttlSeconds % 3600) / 60;
            long seconds = ttlSeconds % 60;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }

    /**
     * Blacklist statistics
     */
    public static class BlacklistStats {
        private int totalBlacklisted;
        private int totalUsersWithTokens;
        private int totalDevicesWithTokens;

        public int getTotalBlacklisted() {
            return totalBlacklisted;
        }

        public void setTotalBlacklisted(int totalBlacklisted) {
            this.totalBlacklisted = totalBlacklisted;
        }

        public int getTotalUsersWithTokens() {
            return totalUsersWithTokens;
        }

        public void setTotalUsersWithTokens(int totalUsersWithTokens) {
            this.totalUsersWithTokens = totalUsersWithTokens;
        }

        public int getTotalDevicesWithTokens() {
            return totalDevicesWithTokens;
        }

        public void setTotalDevicesWithTokens(int totalDevicesWithTokens) {
            this.totalDevicesWithTokens = totalDevicesWithTokens;
        }
    }
}