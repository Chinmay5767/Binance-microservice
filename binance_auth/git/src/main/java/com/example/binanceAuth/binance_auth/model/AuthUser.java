package com.example.binanceAuth.binance_auth.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AuthUser - Request model for login and registration
 * This is what the frontend sends to the backend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUser implements Serializable {

    private static final long serialVersionUID = 1L;

    // ═══════════════════════════════════════════════════════════════
    // 1. USER IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Username (optional during login, required during registration)
     */
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", 
             message = "Username can only contain letters, numbers, dots, underscores and hyphens")
    private String username;

    /**
     * Email ID - Used as primary login identifier
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String emailId;

    /**
     * Password - Required for login and registration
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /**
     * Role - Optional, defaults to ROLE_USER
     */
    private String role;

    // ═══════════════════════════════════════════════════════════════
    // 2. DEVICE INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Device ID for tracking
     */
    private String deviceId;

    /**
     * Device name (e.g., "iPhone 15 Pro")
     */
    private String deviceName;

    /**
     * Device type (MOBILE, TABLET, DESKTOP)
     */
    private String deviceType;

    /**
     * Operating System
     */
    private String os;

    /**
     * OS Version
     */
    private String osVersion;

    /**
     * Browser name
     */
    private String browser;

    /**
     * Browser version
     */
    private String browserVersion;

    /**
     * Screen resolution
     */
    private String screenResolution;

    /**
     * Language preference
     */
    private String language;

    /**
     * Timezone
     */
    private String timezone;

    // ═══════════════════════════════════════════════════════════════
    // 3. REGISTRATION SPECIFIC FIELDS
    // ═══════════════════════════════════════════════════════════════

    /**
     * First name (registration only)
     */
    private String firstName;

    /**
     * Last name (registration only)
     */
    private String lastName;

    /**
     * Phone number (registration only)
     */
    private String phoneNumber;

    /**
     * Confirm password (registration only)
     */
    private String confirmPassword;

    // ═══════════════════════════════════════════════════════════════
    // 4. UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if passwords match (for registration)
     */
    public boolean passwordsMatch() {
        return password != null && password.equals(confirmPassword);
    }

    /**
     * Check if this is a registration request
     */
    public boolean isRegistration() {
        return username != null && !username.isEmpty() && 
               emailId != null && !emailId.isEmpty() &&
               password != null && !password.isEmpty();
    }

    /**
     * Check if this is a login request
     */
    public boolean isLogin() {
        return emailId != null && !emailId.isEmpty() &&
               password != null && !password.isEmpty();
    }

    /**
     * Create minimal AuthUser for login
     */
    public static AuthUser forLogin(String email, String password) {
        return AuthUser.builder()
            .emailId(email)
            .password(password)
            .build();
    }

    /**
     * Create AuthUser for registration
     */
    public static AuthUser forRegistration(String username, String email, String password, String confirmPassword) {
        return AuthUser.builder()
            .username(username)
            .emailId(email)
            .password(password)
            .confirmPassword(confirmPassword)
            .role("ROLE_USER")
            .build();
    }

    /**
     * Mask email for logging (privacy)
     */
    public String getMaskedEmail() {
        if (emailId == null || emailId.isEmpty()) {
            return "null";
        }
        String[] parts = emailId.split("@");
        if (parts.length == 2) {
            String name = parts[0];
            if (name.length() <= 2) {
                return name + "@" + parts[1];
            }
            return name.substring(0, Math.min(3, name.length())) + "***@" + parts[1];
        }
        return "***";
    }

    /**
     * Check if device info is provided
     */
    public boolean hasDeviceInfo() {
        return deviceId != null && !deviceId.isEmpty();
    }

    /**
     * Create DeviceInfo from AuthUser fields
     */
    public DeviceInfo toDeviceInfo() {
        return DeviceInfo.builder()
            .deviceId(deviceId)
            .deviceName(deviceName)
            .deviceType(deviceType)
            .os(os)
            .osVersion(osVersion)
            .browser(browser)
            .browserVersion(browserVersion)
            .screenResolution(screenResolution)
            .language(language)
            .timezone(timezone)
            .build();
    }
}