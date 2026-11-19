package com.example.binanceAuth.binance_auth.controller;

import com.example.binanceAuth.binance_auth.model.ApiResponse;
import com.example.binanceAuth.binance_auth.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth/tokens")
@RequiredArgsConstructor
public class TokenManagementController {

    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Check if a token is blacklisted
     */
    @GetMapping("/check")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse checkToken(@RequestParam String token) {
        boolean isBlacklisted = tokenBlacklistService.isTokenBlacklisted(token);
        var status = tokenBlacklistService.getBlacklistStatus(token);
        
        return ApiResponse.success("Token status retrieved", Map.of(
            "blacklisted", isBlacklisted,
            "ttl", status.getTtlFormatted(),
            "ttlSeconds", status.getTtlSeconds()
        ));
    }

    /**
     * Revoke all tokens for a user
     */
    @PostMapping("/revoke/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse revokeUserTokens(@PathVariable Long userId) {
        tokenBlacklistService.revokeAllUserTokens(userId);
        return ApiResponse.success("All tokens revoked for user: " + userId, null);
    }

    /**
     * Revoke all tokens for a device
     */
    @PostMapping("/revoke/device/{deviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse revokeDeviceTokens(@PathVariable String deviceId) {
        tokenBlacklistService.revokeDeviceTokens(deviceId);
        return ApiResponse.success("All tokens revoked for device: " + deviceId, null);
    }

    /**
     * Get token audit history
     */
    @GetMapping("/audit/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse getTokenAudit(@PathVariable Long userId, 
                                     @RequestParam(defaultValue = "50") int limit) {
        var history = tokenBlacklistService.getTokenAuditHistory(userId, limit);
        return ApiResponse.success("Token audit history retrieved", history);
    }

    /**
     * Get blacklist statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse getBlacklistStats() {
        var stats = tokenBlacklistService.getBlacklistStats();
        return ApiResponse.success("Blacklist statistics retrieved", stats);
    }
}