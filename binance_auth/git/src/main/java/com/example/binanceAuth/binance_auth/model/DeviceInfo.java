package com.example.binanceAuth.binance_auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.springframework.util.DigestUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Device Information Model
 * Captures device details for security, tracking, and risk assessment
 */
@Data
@Builder
@ToString(exclude = {"userAgent", "fingerprint", "ipAddress"}) // Exclude sensitive data from logs
public class DeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    // ═══════════════════════════════════════════════════════════════
    // 1. DEVICE IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Unique device identifier (UUID from client or generated server-side)
     */
    @Builder.Default
    private String deviceId = null;

    /**
     * User-friendly device name (e.g., "iPhone 15 Pro", "Windows PC")
     */
    private String deviceName;

    /**
     * Device type: MOBILE, TABLET, DESKTOP, IOT, UNKNOWN
     */
    @Builder.Default
    private String deviceType = "UNKNOWN";

    // ═══════════════════════════════════════════════════════════════
    // 2. DEVICE HARDWARE DETAILS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Device manufacturer (e.g., "Apple", "Samsung", "Dell")
     */
    private String manufacturer;

    /**
     * Device model (e.g., "iPhone15,2", "SM-G998B")
     */
    private String model;

    /**
     * Device screen resolution (e.g., "1920x1080")
     */
    private String screenResolution;

    /**
     * Device screen size in inches
     */
    private String screenSize;

    // ═══════════════════════════════════════════════════════════════
    // 3. OPERATING SYSTEM
    // ═══════════════════════════════════════════════════════════════

    /**
     * Operating system name (e.g., "iOS", "Android", "Windows", "macOS", "Linux")
     */
    private String os;

    /**
     * Operating system version (e.g., "17.2", "14", "11")
     */
    private String osVersion;

    /**
     * Operating system build number
     */
    private String osBuild;

    // ═══════════════════════════════════════════════════════════════
    // 4. BROWSER INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Browser name (e.g., "Chrome", "Safari", "Firefox", "Edge")
     */
    private String browser;

    /**
     * Browser version (e.g., "120.0.6099.109")
     */
    private String browserVersion;

    /**
     * Browser engine (e.g., "Blink", "WebKit", "Gecko")
     */
    private String browserEngine;

    // ═══════════════════════════════════════════════════════════════
    // 5. NETWORK INFORMATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Client IP address
     */
    private String ipAddress;

    /**
     * IP geolocation (e.g., "New York, USA")
     */
    private String ipLocation;

    /**
     * IP country code (e.g., "US", "IN", "GB")
     */
    private String ipCountry;

    /**
     * IP city
     */
    private String ipCity;

    /**
     * Internet Service Provider
     */
    private String isp;

    /**
     * Network type (e.g., "WiFi", "Cellular", "Ethernet")
     */
    private String networkType;

    // ═══════════════════════════════════════════════════════════════
    // 6. LANGUAGE & LOCALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Accept-Language header (e.g., "en-US,en;q=0.9")
     */
    private String language;

    /**
     * Timezone (e.g., "America/New_York", "Asia/Kolkata")
     */
    private String timezone;

    /**
     * Timezone offset in minutes from UTC
     */
    private Integer timezoneOffset;

    // ═══════════════════════════════════════════════════════════════
    // 7. USER AGENT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Full User-Agent string
     */
    private String userAgent;

    // ═══════════════════════════════════════════════════════════════
    // 8. SECURITY & TRUST
    // ═══════════════════════════════════════════════════════════════

    /**
     * Device fingerprint (hash of device characteristics)
     */
    private String fingerprint;

    /**
     * Whether this device is trusted (skip MFA for this device)
     */
    @Builder.Default
    private boolean trusted = false;

    /**
     * Trust score: 0-100 based on risk factors
     */
    @Builder.Default
    private int trustScore = 0;

    /**
     * Whether this device is suspicious
     */
    @Builder.Default
    private boolean suspicious = false;

    /**
     * Risk assessment reasons
     */
    private String riskReasons;

    /**
     * Risk score: 0-100 (higher = more risky)
     */
    @Builder.Default
    private int riskScore = 0;

    // ═══════════════════════════════════════════════════════════════
    // 9. TIMESTAMPS
    // ═══════════════════════════════════════════════════════════════

    /**
     * When this device was first seen (milliseconds)
     */
    @Builder.Default
    private long firstSeenAt = System.currentTimeMillis();

    /**
     * When this device was last seen (milliseconds)
     */
    @Builder.Default
    private long lastSeenAt = System.currentTimeMillis();

    /**
     * When this device last had a successful login (milliseconds)
     */
    @Builder.Default
    private long lastLoginAt = System.currentTimeMillis();

    /**
     * When this device was trusted (milliseconds)
     */
    private long trustedAt;

    // ═══════════════════════════════════════════════════════════════
    // 10. UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate a unique device fingerprint based on device characteristics
     */
    public String generateFingerprint() {
        String raw = String.join("|",
            deviceName != null ? deviceName : "",
            os != null ? os : "",
            osVersion != null ? osVersion : "",
            browser != null ? browser : "",
            browserVersion != null ? browserVersion : "",
            screenResolution != null ? screenResolution : "",
            language != null ? language : "",
            timezone != null ? timezone : "",
            manufacturer != null ? manufacturer : "",
            model != null ? model : ""
        );
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Check if device is mobile (phone or tablet)
     */
    public boolean isMobileDevice() {
        return deviceType != null && 
               (deviceType.equalsIgnoreCase("MOBILE") || 
                deviceType.equalsIgnoreCase("TABLET"));
    }

    /**
     * Check if device is desktop
     */
    public boolean isDesktopDevice() {
        return deviceType != null && 
               deviceType.equalsIgnoreCase("DESKTOP");
    }

    /**
     * Check if device is suspicious based on various factors
     */
    public boolean isSuspiciousDevice() {
        // High risk score indicates suspicious activity
        if (riskScore > 70) {
            return true;
        }

        // Unknown device without fingerprint
        if (fingerprint == null && deviceId == null) {
            return true;
        }

        // Multiple risk factors
        if (riskScore > 50 && suspicious) {
            return true;
        }

        return false;
    }

    /**
     * Calculate risk score based on device characteristics
     */
    public int calculateRiskScore() {
        int score = 0;

        // Unknown device type
        if (deviceType == null || "UNKNOWN".equals(deviceType)) {
            score += 20;
        }

        // No fingerprint
        if (fingerprint == null) {
            score += 15;
        }

        // No device ID
        if (deviceId == null) {
            score += 10;
        }

        // Suspicious IP (would need IP reputation service)
        if (ipCountry != null && isHighRiskCountry(ipCountry)) {
            score += 25;
        }

        // Unusual user agent (would need UA parser)
        if (userAgent != null && isBotUserAgent(userAgent)) {
            score += 30;
        }

        // New device (first seen recently)
        if (System.currentTimeMillis() - firstSeenAt < 3600000) { // 1 hour
            score += 10;
        }

        return Math.min(score, 100);
    }

    /**
     * Check if country is high risk for fraud
     */
    private boolean isHighRiskCountry(String country) {
        // List of high-risk countries (example)
        return country.equals("XX") || country.equals("YY") || country.equals("ZZ");
    }

    /**
     * Check if user agent is from a bot
     */
    private boolean isBotUserAgent(String userAgent) {
        String lower = userAgent.toLowerCase();
        return lower.contains("bot") || 
               lower.contains("crawler") || 
               lower.contains("spider") ||
               lower.contains("scraper");
    }

    /**
     * Validate device information completeness
     */
    public boolean isValid() {
        return deviceId != null && 
               !deviceId.isEmpty() &&
               userAgent != null &&
               !userAgent.isEmpty();
    }

    /**
     * Create a minimal DeviceInfo for logging
     */
    public String toLogString() {
        return String.format("Device[id=%s, type=%s, ip=%s, os=%s, browser=%s]",
            deviceId, deviceType, ipAddress, os, browser);
    }

    /**
     * Update last seen timestamp
     */
    public void updateLastSeen() {
        this.lastSeenAt = System.currentTimeMillis();
    }

    /**
     * Mark device as trusted
     */
    public void markTrusted() {
        this.trusted = true;
        this.trustedAt = System.currentTimeMillis();
        this.trustScore = 100;
    }

    /**
     * Mark device as untrusted
     */
    public void markUntrusted() {
        this.trusted = false;
        this.trustedAt = 0;
        this.trustScore = 0;
    }

    /**
     * Create DeviceInfo from User-Agent string
     */
    public static DeviceInfo fromUserAgent(String userAgent, String ipAddress) {
        return DeviceInfo.builder()
            .userAgent(userAgent)
            .ipAddress(ipAddress)
            .firstSeenAt(System.currentTimeMillis())
            .lastSeenAt(System.currentTimeMillis())
            .build();
    }

    /**
     * Create a minimal DeviceInfo for new device
     */
    public static DeviceInfo minimal(String deviceId, String ipAddress, String userAgent) {
        DeviceInfo deviceInfo = DeviceInfo.builder()
            .deviceId(deviceId)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .build();
        
        // Generate fingerprint
        deviceInfo.setFingerprint(deviceInfo.generateFingerprint());
        
        return deviceInfo;
    }
}