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
 * UserRegistrationResult - Result of user registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    // ═══════════════════════════════════════════════════════════════
    // 1. USER IDENTIFICATION
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
    // 2. USER DETAILS
    // ═══════════════════════════════════════════════════════════════

    /**
     * First name
     */
    private String firstName;

    /**
     * Last name
     */
    private String lastName;

    /**
     * Phone number
     */
    private String phoneNumber;

    // ═══════════════════════════════════════════════════════════════
    // 3. ACCOUNT STATUS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Whether account is enabled
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Whether email is verified
     */
    @Builder.Default
    private boolean emailVerified = false;

    /**
     * Whether MFA is enabled
     */
    @Builder.Default
    private boolean mfaEnabled = false;

    // ═══════════════════════════════════════════════════════════════
    // 4. KEYCLOAK INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Keycloak user ID
     */
    private String keycloakId;

    // ═══════════════════════════════════════════════════════════════
    // 5. REGISTRATION STATUS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Whether registration was successful
     */
    @Builder.Default
    private boolean success = false;

    /**
     * Response message
     */
    private String message;

    /**
     * Verification token (for email verification)
     */
    private String verificationToken;

    /**
     * Registration timestamp
     */
    @Builder.Default
    private long registeredAt = System.currentTimeMillis();

    // ═══════════════════════════════════════════════════════════════
    // 6. FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create success result from user entity
     */
    public static UserRegistrationResult success(AuthUserEntity user) {
        return UserRegistrationResult.builder()
            .userId(user.getUserId())
            .username(user.getUsername())
            .email(user.getEmailId())
            .roles(user.getRoles())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .enabled(user.isEnabled())
            .emailVerified(user.isEmailVerified())
            .keycloakId(user.getKeycloakId())
            .success(true)
            .message("User registered successfully")
            .registeredAt(System.currentTimeMillis())
            .build();
    }

    /**
     * Create success result with verification token
     */
    public static UserRegistrationResult successWithVerification(AuthUserEntity user, String verificationToken) {
        UserRegistrationResult result = success(user);
        result.setVerificationToken(verificationToken);
        result.setMessage("User registered. Please verify your email.");
        return result;
    }

    /**
     * Create failure result
     */
    public static UserRegistrationResult failure(String errorMessage) {
        return UserRegistrationResult.builder()
            .success(false)
            .message(errorMessage)
            .build();
    }

    /**
     * Create failure result with detailed error
     */
    public static UserRegistrationResult failure(String errorMessage, String errorCode) {
        return UserRegistrationResult.builder()
            .success(false)
            .message(errorMessage)
            .build();
    }

    /**
     * Add verification token to result
     */
    public UserRegistrationResult withVerificationToken(String token) {
        this.verificationToken = token;
        this.message = "Registration successful. Please check your email to verify your account.";
        return this;
    }

    /**
     * Convert to response map
     */
    public Map<String, Object> toResponseMap() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("userId", userId);
        response.put("username", username);
        response.put("email", email);
        response.put("roles", roles);
        response.put("emailVerified", emailVerified);
        response.put("mfaEnabled", mfaEnabled);
        response.put("registeredAt", registeredAt);
        
        if (verificationToken != null) {
            response.put("verificationToken", verificationToken);
        }
        
        return response;
    }
}