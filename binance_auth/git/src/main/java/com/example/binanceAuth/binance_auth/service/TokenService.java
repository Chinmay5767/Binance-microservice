package com.example.binanceAuth.binance_auth.service;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.model.DeviceInfo;
import com.example.binanceAuth.binance_auth.model.TokenPair;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.crypto.SecretKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${app.security.jwt.access-token-expiration:900000}")
    private long accessTokenExpiration; // 15 minutes
    
    @Value("${app.security.jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration; // 7 days
    
    @Autowired
    AuthUserRepo authUserRepo;

    private final RedisTemplate<String, String> redisTemplate;
    private final TokenBlacklistService tokenBlacklistService;

    // ═══════════════════════════════════════════════════════════════
    // 1. GENERATE TOKENS (Public Methods)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate both Access and Refresh tokens with DeviceInfo
     */
    public TokenPair generateTokens(AuthUserEntity user, DeviceInfo deviceInfo) {
        log.debug("Generating tokens for user: {}", user.getEmailId());
        
        String accessToken = generateAccessToken(user, deviceInfo);
        String refreshToken = generateRefreshToken(user, deviceInfo);
        
        storeRefreshToken(refreshToken, user, deviceInfo);
        
        return TokenPair.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .accessTokenExpiry(System.currentTimeMillis() + accessTokenExpiration)
            .refreshTokenExpiry(System.currentTimeMillis() + refreshTokenExpiration)
            .expiresIn(accessTokenExpiration / 1000)
            .tokenType("Bearer")
            .userId(user.getUserId())
            .username(user.getUsername())
            .email(user.getEmailId())
            .roles(user.getRoles())
            .deviceId(deviceInfo != null ? deviceInfo.getDeviceId() : null)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. ACCESS TOKEN GENERATION (Overloaded Methods)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate Access Token with DeviceInfo object
     */
    public String generateAccessToken(AuthUserEntity user, DeviceInfo deviceInfo) {
        Key key = getSigningKey();
        
        return Jwts.builder()
            .setSubject(user.getUserId().toString())
            .claim("email", user.getEmailId())
            .claim("username", user.getUsername())
            .claim("roles", user.getRoles())
            .claim("deviceId", deviceInfo != null ? deviceInfo.getDeviceId() : null)
            .claim("tokenType", "access")
            .setIssuedAt(new Date())
            .setIssuer("auth-service")
            .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(key)
            .compact();
    }

    /**
     * Generate Access Token with String deviceId (Overloaded)
     */
    public String generateAccessToken(AuthUserEntity user, String deviceId) {
        // Create DeviceInfo from deviceId
        DeviceInfo deviceInfo = DeviceInfo.builder()
            .deviceId(deviceId != null ? deviceId : "unknown")
            .build();
        return generateAccessToken(user, deviceInfo);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. REFRESH TOKEN GENERATION (Overloaded Methods)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate Refresh Token with DeviceInfo object
     */
    public String generateRefreshToken(AuthUserEntity user, DeviceInfo deviceInfo) {
        Key key = getSigningKey();
        
        String refreshTokenId = UUID.randomUUID().toString();
        
        return Jwts.builder()
            .setSubject(user.getUserId().toString())
            .claim("email", user.getEmailId())
            .claim("deviceId", deviceInfo != null ? deviceInfo.getDeviceId() : null)
            .claim("tokenType", "refresh")
            .claim("tokenId", refreshTokenId)
            .setIssuedAt(new Date())
            .setIssuer("auth-service")
            .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
            .signWith(key)
            .compact();
    }

    /**
     * Generate Refresh Token with String deviceId (Overloaded)
     */
    public String generateRefreshToken(AuthUserEntity user, String deviceId) {
        // Create DeviceInfo from deviceId
        DeviceInfo deviceInfo = DeviceInfo.builder()
            .deviceId(deviceId != null ? deviceId : "unknown")
            .build();
        return generateRefreshToken(user, deviceInfo);
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. JWT TOKEN GENERATION (For AuthController)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate JWT token from user details
     */
    public String generateToken(String userId, String email, String username, List<String> roles) {
        return generateToken(userId, email, username, roles, "PASSWORD");
    }

    public String generateToken(String userId, String email, String username, List<String> roles, String authLevel) {
        Key key = getSigningKey();
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("username", username);
        claims.put("roles", roles);
        claims.put("tokenType", "access");
        claims.put("authLevel", authLevel);
        claims.put("mfaUsed", "MFA".equals(authLevel));
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(userId)
            .setIssuedAt(new Date())
            .setIssuer("auth-service")
            .setExpiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .signWith(key)
            .compact();
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. STORE REFRESH TOKEN IN REDIS
    // ═══════════════════════════════════════════════════════════════

    private void storeRefreshToken(String refreshToken, AuthUserEntity user, DeviceInfo deviceInfo) {
        String key = "refresh:token:" + refreshToken;
        String value = String.format("%s|%s|%s", 
            user.getUserId(), 
            deviceInfo != null ? deviceInfo.getDeviceId() : "unknown",
            System.currentTimeMillis()
        );
        
        redisTemplate.opsForValue().set(key, value, refreshTokenExpiration, TimeUnit.MILLISECONDS);
        
        String userKey = "user:" + user.getUserId() + ":refresh_tokens";
        redisTemplate.opsForSet().add(userKey, refreshToken);
        redisTemplate.expire(userKey, refreshTokenExpiration, TimeUnit.MILLISECONDS);
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. REFRESH TOKENS
    // ═══════════════════════════════════════════════════════════════

    public TokenPair refreshTokens(String refreshToken, String deviceId) {
        log.debug("Refreshing tokens");
        
        Claims claims = validateRefreshToken(refreshToken);
        Long userId = Long.parseLong(claims.getSubject());
        String storedDeviceId = claims.get("deviceId", String.class);
        
        String key = "refresh:token:" + refreshToken;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        AuthUserEntity user = authUserRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        redisTemplate.delete(key);
        String userKey = "user:" + userId + ":refresh_tokens";
        redisTemplate.opsForSet().remove(userKey, refreshToken);
        
        DeviceInfo deviceInfo = DeviceInfo.builder()
            .deviceId(storedDeviceId != null ? storedDeviceId : deviceId)
            .build();
        
        return generateTokens(user, deviceInfo);
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. VALIDATE REFRESH TOKEN
    // ═══════════════════════════════════════════════════════════════

    public Claims validateRefreshToken(String token) {
        Key key = getSigningKey();
        
        Claims claims = Jwts.parser()
            .verifyWith((SecretKey) key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        
        if (!"refresh".equals(claims.get("tokenType", String.class))) {
            throw new RuntimeException("Invalid token type");
        }
        
        if (claims.getExpiration().before(new Date())) {
            throw new RuntimeException("Refresh token expired");
        }
        
        return claims;
    }

    // ═══════════════════════════════════════════════════════════════
    // 8. TOKEN VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !isTokenExpired(token) 
                && !tokenBlacklistService.isTokenBlacklisted(token)
                && "access".equals(claims.get("tokenType"));
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 9. TOKEN EXTRACTION METHODS
    // ═══════════════════════════════════════════════════════════════

    public Claims extractAllClaims(String token) {
        Key key = getSigningKey();
        return Jwts.parser()
            .verifyWith((SecretKey) key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractUserId(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("Failed to extract user ID: {}", e.getMessage());
            return null;
        }
    }

    public Long extractUserIdLong(String token) {
        try {
            String userId = extractUserId(token);
            return userId != null ? Long.parseLong(userId) : null;
        } catch (Exception e) {
            log.warn("Failed to extract user ID as Long: {}", e.getMessage());
            return null;
        }
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    public String extractUsername(String token) {
        return extractClaim(token, claims -> claims.get("username", String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", List.class));
    }

    public String extractDeviceId(String token) {
        return extractClaim(token, claims -> claims.get("deviceId", String.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("tokenType", String.class));
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public long getRemainingTime(String token) {
        try {
            Date expiration = extractExpiration(token);
            long remaining = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remaining / 1000);
        } catch (Exception e) {
            return 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 10. TOKEN BLACKLIST
    // ═══════════════════════════════════════════════════════════════

    public void blacklistToken(String token, Long userId, String deviceId) {
        tokenBlacklistService.blacklistToken(token, userId, deviceId);
    }

    public void blacklistToken(String token) {
        tokenBlacklistService.blacklistToken(token, null, null);
    }

    // ═══════════════════════════════════════════════════════════════
    // 11. UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}