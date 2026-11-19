package com.example.binanceAuth.binance_auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;

/**
 * Token Pair Model
 * Contains both Access and Refresh tokens with metadata
 */
@Data
@Builder
@ToString(exclude = {"accessToken", "refreshToken"}) // Don't log tokens
public class TokenPair implements Serializable {

    private static final long serialVersionUID = 1L;

    // ═══════════════════════════════════════════════════════════════
    // 1. TOKENS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Access Token - JWT for API authorization
     * Short-lived (15 minutes)
     */
    private String accessToken;

    /**
     * Refresh Token - JWT for obtaining new access tokens
     * Long-lived (7 days)
     */
    private String refreshToken;

    // ═══════════════════════════════════════════════════════════════
    // 2. TOKEN METADATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Token type (always "Bearer" for JWT)
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Token scope (e.g., "read write", "openid profile email")
     */
    @Builder.Default
    private String scope = "read write";

    /**
     * Token issuer
     */
    @Builder.Default
    private String issuer = "auth-service";

    // ═══════════════════════════════════════════════════════════════
    // 3. EXPIRY INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Access token expiry timestamp (milliseconds)
     */
    private long accessTokenExpiry;

    /**
     * Refresh token expiry timestamp (milliseconds)
     */
    private long refreshTokenExpiry;

    /**
     * Access token expiry in seconds (convenience field)
     */
    private long expiresIn;

    // ═══════════════════════════════════════════════════════════════
    // 4. USER CONTEXT
    // ═══════════════════════════════════════════════════════════════

    /**
     * User ID
     */
    private Long userId;

    /**
     * Username
     */
    private String username;

    /**
     * User email
     */
    private String email;

    /**
     * User roles
     */
    private List<String> roles;

    // ═══════════════════════════════════════════════════════════════
    // 5. DEVICE CONTEXT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Device ID associated with these tokens
     */
    private String deviceId;

    /**
     * Session ID
     */
    private String sessionId;

    // ═══════════════════════════════════════════════════════════════
    // 6. SECURITY CONTEXT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Authentication level (e.g., "PASSWORD", "MFA", "BIOMETRIC")
     */
    @Builder.Default
    private String authLevel = "PASSWORD";

    /**
     * Whether MFA was used for this token
     */
    @Builder.Default
    private boolean mfaUsed = false;

    /**
     * Trust score for this session (0-100)
     */
    @Builder.Default
    private int trustScore = 100;

    // ═══════════════════════════════════════════════════════════════
    // 7. UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if access token is valid (not expired)
     */
    public boolean isAccessTokenValid() {
        return accessToken != null && 
               !accessToken.isEmpty() &&
               System.currentTimeMillis() < accessTokenExpiry;
    }

    /**
     * Check if refresh token is valid (not expired)
     */
    public boolean isRefreshTokenValid() {
        return refreshToken != null && 
               !refreshToken.isEmpty() &&
               System.currentTimeMillis() < refreshTokenExpiry;
    }

    /**
     * Check if token is expired (both access and refresh)
     */
    public boolean isExpired() {
        return !isAccessTokenValid() || !isRefreshTokenValid();
    }

    /**
     * Get remaining time for access token in seconds
     */
    public long getAccessTokenRemainingSeconds() {
        if (accessTokenExpiry <= 0) {
            return 0;
        }
        long remaining = (accessTokenExpiry - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * Get remaining time for refresh token in seconds
     */
    public long getRefreshTokenRemainingSeconds() {
        if (refreshTokenExpiry <= 0) {
            return 0;
        }
        long remaining = (refreshTokenExpiry - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * Get formatted expiration time
     */
    public String getFormattedExpiry() {
        return String.format("Access: %ds, Refresh: %ds", 
            getAccessTokenRemainingSeconds(), 
            getRefreshTokenRemainingSeconds());
    }

    /**
     * Create TokenPair from user and tokens
     */
    public static TokenPair fromUser(AuthUserEntity user, 
                                     String accessToken, 
                                     String refreshToken,
                                     long accessTokenExpiry,
                                     long refreshTokenExpiry,
                                     String deviceId) {
        return TokenPair.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .accessTokenExpiry(accessTokenExpiry)
            .refreshTokenExpiry(refreshTokenExpiry)
            .expiresIn((accessTokenExpiry - System.currentTimeMillis()) / 1000)
            .tokenType("Bearer")
            .scope("read write")
            .issuer("auth-service")
            .userId(user.getUserId())
            .username(user.getUsername())
            .email(user.getEmailId())
            .roles(user.getRoles())
            .deviceId(deviceId)
            .authLevel("PASSWORD")
            .mfaUsed(false)
            .trustScore(100)
            .build();
    }

    /**
     * Create TokenPair for MFA-authenticated user
     */
    public static TokenPair mfaAuthenticated(AuthUserEntity user,
                                             String accessToken,
                                             String refreshToken,
                                             long accessTokenExpiry,
                                             long refreshTokenExpiry,
                                             String deviceId) {
        TokenPair pair = fromUser(user, accessToken, refreshToken, 
            accessTokenExpiry, refreshTokenExpiry, deviceId);
        pair.setAuthLevel("MFA");
        pair.setMfaUsed(true);
        pair.setTrustScore(100);
        return pair;
    }

    /**
     * Create TokenPair for biometric-authenticated user
     */
    public static TokenPair biometricAuthenticated(AuthUserEntity user,
                                                    String accessToken,
                                                    String refreshToken,
                                                    long accessTokenExpiry,
                                                    long refreshTokenExpiry,
                                                    String deviceId) {
        TokenPair pair = fromUser(user, accessToken, refreshToken,
            accessTokenExpiry, refreshTokenExpiry, deviceId);
        pair.setAuthLevel("BIOMETRIC");
        pair.setMfaUsed(true);
        pair.setTrustScore(100);
        return pair;
    }

    /**
     * Convert to response map for API
     */
    public Map<String, Object> toResponseMap() {
        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("tokenType", tokenType);
        response.put("expiresIn", expiresIn);
        response.put("scope", scope);
        response.put("userId", userId);
        response.put("username", username);
        response.put("email", email);
        response.put("roles", roles);
        response.put("authLevel", authLevel);
        response.put("mfaUsed", mfaUsed);
        return response;
    }

    /**
     * Get masked tokens for logging
     */
    public String getMaskedTokens() {
        return String.format("Access: %s... (expires in %ds), Refresh: %s... (expires in %ds)",
            accessToken != null ? accessToken.substring(0, Math.min(10, accessToken.length())) : "null",
            getAccessTokenRemainingSeconds(),
            refreshToken != null ? refreshToken.substring(0, Math.min(10, refreshToken.length())) : "null",
            getRefreshTokenRemainingSeconds()
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. INNER CLASSES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Token validation result
     */
    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String reason;
        private Long userId;
        private String email;
        private List<String> roles;
        private String deviceId;
        private boolean mfaUsed;
        private String authLevel;
        private long remainingSeconds;
    }

    /**
     * Token refresh result
     */
    @Data
    @Builder
    public static class RefreshResult {
        private boolean success;
        private String message;
        private TokenPair newTokens;
        private int rotationCount;
        private boolean rotationLimitReached;
    }
}