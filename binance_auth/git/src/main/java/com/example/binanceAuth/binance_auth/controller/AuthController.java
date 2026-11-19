package com.example.binanceAuth.binance_auth.controller;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.model.ApiResponse;
import com.example.binanceAuth.binance_auth.model.UserInfo;
import com.example.binanceAuth.binance_auth.model.UserSession;
import com.example.binanceAuth.binance_auth.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor  // ✅ Add this for constructor injection
public class AuthController {
    @Autowired
    private final UserService userService;
    @Autowired
    private final TokenService tokenService;
    @Autowired
    private final TokenBlacklistService tokenBlacklistService;
    @Autowired
    private final RateLimitingService rateLimitingService;
    @Autowired
    private final SessionService sessionService;
    @Autowired
    private final UserCacheService userCacheService;
    @Autowired
    private final MfaService mfaService;

    // ═══════════════════════════════════════════════════════════════
    // 1. LOGIN SUCCESS
    // ═══════════════════════════════════════════════════════════════
    @GetMapping("/success")
    public ResponseEntity<ApiResponse> loginSuccess(
            @AuthenticationPrincipal OidcUser oidcUser,
            HttpServletRequest request) {
        
        log.info("Login successful for user: {}", oidcUser.getPreferredUsername());
        
        try {
            String email = oidcUser.getEmail();
            String ip = request.getRemoteAddr();
            
            // ✅ Check rate limit
            if (rateLimitingService.isLoginRateLimited(email, ip)) {
                return ResponseEntity.status(429)
                    .body(ApiResponse.error("Too many login attempts"));
            }
            
            // ✅ Sync user to database
            UserInfo userInfo = userService.syncUser(oidcUser);
            AuthUserEntity userEntity = userService.getUserEntity(Long.parseLong(userInfo.getId()));

            // ✅ Reset rate limit
            rateLimitingService.resetRateLimit("login:" + email + ":" + ip);

            // ✅ Email OTP MFA challenge when enabled
            if (userEntity != null && userEntity.isMfaEnabled()) {
                Map<String, Object> mfaChallenge = mfaService.initiateLoginChallenge(
                    userEntity,
                    ip,
                    request.getHeader("User-Agent"),
                    request.getHeader("X-Device-Id")
                );
                return ResponseEntity.ok(ApiResponse.success("MFA verification required", mfaChallenge));
            }

            // ✅ Generate JWT token
            String token = tokenService.generateToken(
                userInfo.getId(),
                userInfo.getEmail(),
                userInfo.getUsername(),
                userInfo.getRoles()
            );

            // ✅ Create session in Redis
            UserSession session = UserSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(userInfo.getId())
                .email(userInfo.getEmail())
                .username(userInfo.getUsername())
                .userAgent(request.getHeader("User-Agent"))
                .ipAddress(ip)
                .deviceId(request.getHeader("X-Device-Id"))
                .createdAt(System.currentTimeMillis())
                .lastActivityAt(System.currentTimeMillis())
                .expiresAt(System.currentTimeMillis() + 86400000)
                .isActive(true)
                .roles(userInfo.getRoles())
                .build();

            sessionService.storeSession(session);

            // ✅ Cache user data in Redis
            if (userEntity != null) {
                userCacheService.cacheUser(userEntity);
            }

            // ✅ Build response
            return ResponseEntity.ok(ApiResponse.success("Login successful", Map.of(
                "accessToken", token,
                "tokenType", "Bearer",
                "expiresIn", 900,
                "sessionId", session.getSessionId(),
                "user", userInfo
            )));
            
        } catch (Exception e) {
            log.error("Login success processing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to process login"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. LOGOUT
    // ═══════════════════════════════════════════════════════════════
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request) {
        
        log.info("Logout request");
        
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                // ✅ Extract user info from token - FIXED: returns String
                String userIdStr = tokenService.extractUserId(token);
                String deviceId = tokenService.extractDeviceId(token);
                
                // ✅ Convert to Long for database operations
                Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;
                
                // ✅ Blacklist token
                tokenBlacklistService.blacklistToken(token, userId, deviceId);
                
                // ✅ Invalidate session
                if (sessionId != null) {
                    sessionService.invalidateSession(sessionId);
                }
                
                // ✅ Invalidate user cache
                if (userId != null) {
                    userCacheService.invalidateUserCache(userId);
                }
                
                log.info("User logged out: {}", userId);
            }
            
            return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
            
        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Logout failed"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. LOGOUT ALL DEVICES
    // ═══════════════════════════════════════════════════════════════
    @PostMapping("/logout/all")
    public ResponseEntity<ApiResponse> logoutAllDevices(
            @RequestHeader("Authorization") String authHeader) {
        
        log.info("Logout all devices request");
        
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                // ✅ Extract userId as String then convert to Long
                String userIdStr = tokenService.extractUserId(token);
                Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;
                
                if (userId != null) {
                    // ✅ Revoke ALL tokens
                    tokenBlacklistService.revokeAllUserTokens(userId);
                    
                    // ✅ Invalidate ALL sessions
                    sessionService.invalidateAllSessions(userId);
                    
                    // ✅ Invalidate user cache
                    userCacheService.invalidateUserCache(userId);
                }
                
                log.info("All devices logged out for user: {}", userId);
            }
            
            return ResponseEntity.ok(ApiResponse.success("Logged out from all devices", null));
            
        } catch (Exception e) {
            log.error("Logout all devices failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Logout all devices failed"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. GET USER INFO
    // ═══════════════════════════════════════════════════════════════
    @GetMapping("/userinfo")
    public ResponseEntity<ApiResponse> getUserInfo(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                // ✅ Extract userId as String
                String userIdStr = tokenService.extractUserId(token);
                if (userIdStr == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid token"));
                }
                
                Long userId = Long.parseLong(userIdStr);
                
                // ✅ Try from cache first
                var cachedUser = userCacheService.getCachedUser(userId);
                if (cachedUser != null) {
                    return ResponseEntity.ok(ApiResponse.success("User info retrieved", 
                        UserInfo.builder()
                            .id(String.valueOf(cachedUser.getUserId()))
                            .username(cachedUser.getUsername())
                            .email(cachedUser.getEmailId())
                            .roles(cachedUser.getRoles())
                            .emailVerified(cachedUser.isEmailVerified())
                            .build()
                    ));
                }
                
                // ✅ Fallback to database
                var user = userService.getUserEntity(userId);
                if (user != null) {
                    return ResponseEntity.ok(ApiResponse.success("User info retrieved",
                        UserInfo.builder()
                            .id(String.valueOf(user.getUserId()))
                            .username(user.getUsername())
                            .email(user.getEmailId())
                            .roles(user.getRoles())
                            .emailVerified(user.isEmailVerified())
                            .build()
                    ));
                }
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Not authenticated"));
            
        } catch (Exception e) {
            log.error("Failed to get user info: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to get user info"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. GET SESSIONS
    // ═══════════════════════════════════════════════════════════════
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse> getSessions(
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String userIdStr = tokenService.extractUserId(token);
                Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;
                
                if (userId != null) {
                    var sessions = sessionService.getUserSessions(userId);
                    return ResponseEntity.ok(ApiResponse.success("Sessions retrieved", sessions));
                }
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Not authenticated"));
            
        } catch (Exception e) {
            log.error("Failed to get sessions: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to get sessions"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. REVOKE SESSION
    // ═══════════════════════════════════════════════════════════════
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse> revokeSession(
            @PathVariable String sessionId,
            @RequestHeader("Authorization") String authHeader) {
        
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String userIdStr = tokenService.extractUserId(token);
                Long userId = userIdStr != null ? Long.parseLong(userIdStr) : null;
                
                if (userId == null) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid token"));
                }
                
                // Verify session belongs to user
                var session = sessionService.getSession(sessionId);
                if (session == null || !session.getUserId().equals(String.valueOf(userId))) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("Session not found or doesn't belong to user"));
                }
                
                // Revoke session
                sessionService.invalidateSession(sessionId);
                
                return ResponseEntity.ok(ApiResponse.success("Session revoked", null));
            }
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Not authenticated"));
            
        } catch (Exception e) {
            log.error("Failed to revoke session: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to revoke session"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. HEALTH CHECK
    // ═══════════════════════════════════════════════════════════════
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "auth-service",
            "authType", "Keycloak OIDC",
            "redisConnected", true,
            "timestamp", System.currentTimeMillis()
        ));
    }
}