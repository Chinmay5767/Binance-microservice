package com.example.binanceAuth.binance_auth.model;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LoginResult - Result of login attempt with complete context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult implements Serializable {

    private static final long serialVersionUID = 1L;

    // ═══════════════════════════════════════════════════════════════
    // 1. USER INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * User entity from database
     */
    private AuthUserEntity user;

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
    // 2. AUTHENTICATION STATUS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Whether authentication was successful
     */
    @Builder.Default
    private boolean authenticated = false;

    /**
     * Authentication provider used (KEYCLOAK, LOCAL, LDAP)
     */
    @Builder.Default
    private String authProvider = "KEYCLOAK";

    /**
     * Authentication level (PASSWORD, MFA, BIOMETRIC)
     */
    @Builder.Default
    private String authLevel = "PASSWORD";

    /**
     * Whether MFA was required
     */
    @Builder.Default
    private boolean mfaRequired = false;

    /**
     * MFA token for verification (if MFA is required)
     */
    private String mfaToken;

    /**
     * MFA type (EMAIL, SMS)
     */
    private MfaType mfaType;

    // ═══════════════════════════════════════════════════════════════
    // 3. DEVICE INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Device information
     */
    private DeviceInfo deviceInfo;

    /**
     * Device ID
     */
    private String deviceId;

    /**
     * Whether device is trusted
     */
    @Builder.Default
    private boolean deviceTrusted = false;

    // ═══════════════════════════════════════════════════════════════
    // 4. SESSION INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * IP address of the request
     */
    private String ipAddress;

    /**
     * User agent
     */
    private String userAgent;

    // ═══════════════════════════════════════════════════════════════
    // 5. TIMESTAMPS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Login timestamp
     */
    @Builder.Default
    private long loginTimestamp = System.currentTimeMillis();

    /**
     * Session expiry timestamp
     */
    private long expiresAt;

    // ═══════════════════════════════════════════════════════════════
    // 6. RISK ASSESSMENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Risk score (0-100)
     */
    @Builder.Default
    private int riskScore = 0;

    /**
     * Whether suspicious activity was detected
     */
    @Builder.Default
    private boolean suspiciousActivity = false;

    /**
     * Risk assessment reasons
     */
    private String riskReasons;

    // ═══════════════════════════════════════════════════════════════
    // 7. TOKENS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Access token (if authenticated)
     */
    private String accessToken;

    /**
     * Refresh token (if authenticated)
     */
    private String refreshToken;

    /**
     * Token expiry in seconds
     */
    private long expiresIn;

    // ═══════════════════════════════════════════════════════════════
    // 8. FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create successful login result
     */
    public static LoginResult success(AuthUserEntity user, DeviceInfo deviceInfo, String sessionId) {
        return LoginResult.builder()
            .user(user)
            .userId(user.getUserId())
            .username(user.getUsername())
            .email(user.getEmailId())
            .roles(user.getRoles())
            .authenticated(true)
            .authProvider("KEYCLOAK")
            .authLevel("PASSWORD")
            .mfaRequired(user.isMfaEnabled())
            .deviceInfo(deviceInfo)
            .deviceId(deviceInfo != null ? deviceInfo.getDeviceId() : null)
            .deviceTrusted(deviceInfo != null && deviceInfo.isTrusted())
            .sessionId(sessionId)
            .loginTimestamp(System.currentTimeMillis())
            .riskScore(deviceInfo != null ? deviceInfo.getRiskScore() : 0)
            .suspiciousActivity(deviceInfo != null && deviceInfo.isSuspiciousDevice())
            .build();
    }

    /**
     * Create MFA required result
     */
    public static LoginResult mfaRequired(AuthUserEntity user, String mfaToken, MfaType mfaType) {
        return LoginResult.builder()
            .user(user)
            .userId(user.getUserId())
            .username(user.getUsername())
            .email(user.getEmailId())
            .authenticated(false)
            .mfaRequired(true)
            .mfaToken(mfaToken)
            .mfaType(mfaType)
            .build();
    }

    /**
     * Create failed login result
     */
    public static LoginResult failed(String reason) {
        return LoginResult.builder()
            .authenticated(false)
            .build();
    }

    /**
     * Add tokens to login result
     */
    public LoginResult withTokens(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.authenticated = true;
        return this;
    }

    /**
     * Convert to response map
     */
    public Map<String, Object> toResponseMap() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", authenticated);
        response.put("authenticated", authenticated);
        response.put("userId", userId);
        response.put("username", username);
        response.put("email", email);
        response.put("roles", roles);
        response.put("authLevel", authLevel);
        response.put("mfaRequired", mfaRequired);
        response.put("sessionId", sessionId);
        response.put("loginTimestamp", loginTimestamp);
        
        if (authenticated) {
            response.put("accessToken", accessToken);
            response.put("refreshToken", refreshToken);
            response.put("expiresIn", expiresIn);
            response.put("tokenType", "Bearer");
        }
        
        if (mfaRequired) {
            response.put("mfaToken", mfaToken);
            response.put("mfaType", mfaType);
        }
        
        return response;
    }
}