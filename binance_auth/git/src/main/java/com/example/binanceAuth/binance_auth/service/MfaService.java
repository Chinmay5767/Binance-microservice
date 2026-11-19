package com.example.binanceAuth.binance_auth.service;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.model.MfaChallenge;
import com.example.binanceAuth.binance_auth.model.MfaType;
import com.example.binanceAuth.binance_auth.model.UserInfo;
import com.example.binanceAuth.binance_auth.model.UserSession;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String CHALLENGE_KEY_PREFIX = "mfa:challenge:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final AuthUserRepo authUserRepo;
    private final TokenService tokenService;
    private final SessionService sessionService;
    private final UserCacheService userCacheService;
    private final RateLimitingService rateLimitingService;

    @Value("${app.mfa.otp.expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${app.mfa.otp.max-attempts:5}")
    private int maxAttempts;

    public Map<String, Object> initiateLoginChallenge(AuthUserEntity user, String ip, String userAgent, String deviceId) {
        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Email must be verified before MFA can be used");
        }

        String challengeToken = createAndSendChallenge(
            user,
            MfaChallenge.Purpose.LOGIN,
            ip,
            userAgent,
            deviceId
        );

        return Map.of(
            "mfaRequired", true,
            "mfaToken", challengeToken,
            "mfaType", MfaType.EMAIL.name(),
            "email", maskEmail(user.getEmailId()),
            "message", "Verification code sent to your email"
        );
    }

    public Map<String, Object> verifyLoginOtp(String challengeToken, String otp, String ip) {
        MfaChallenge challenge = getChallenge(challengeToken);
        if (challenge.getPurpose() != MfaChallenge.Purpose.LOGIN) {
            throw new IllegalArgumentException("Invalid MFA challenge");
        }

        verifyOtp(challengeToken, challenge, otp);

        AuthUserEntity user = authUserRepo.findById(challenge.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        deleteChallenge(challengeToken);
        rateLimitingService.resetRateLimit("mfa:verify:" + challengeToken);

        String token = tokenService.generateToken(
            String.valueOf(user.getUserId()),
            user.getEmailId(),
            user.getUsername(),
            user.getRoles(),
            "MFA"
        );

        UserSession session = UserSession.builder()
            .sessionId(UUID.randomUUID().toString())
            .userId(String.valueOf(user.getUserId()))
            .email(user.getEmailId())
            .username(user.getUsername())
            .userAgent(challenge.getUserAgent())
            .ipAddress(ip != null ? ip : challenge.getIpAddress())
            .deviceId(challenge.getDeviceId())
            .createdAt(System.currentTimeMillis())
            .lastActivityAt(System.currentTimeMillis())
            .expiresAt(System.currentTimeMillis() + 86400000)
            .isActive(true)
            .roles(user.getRoles())
            .build();

        sessionService.storeSession(session);
        userCacheService.cacheUser(user);

        UserInfo userInfo = UserInfo.builder()
            .id(String.valueOf(user.getUserId()))
            .username(user.getUsername())
            .email(user.getEmailId())
            .roles(user.getRoles())
            .emailVerified(user.isEmailVerified())
            .build();

        return Map.of(
            "accessToken", token,
            "tokenType", "Bearer",
            "expiresIn", 900,
            "authLevel", "MFA",
            "sessionId", session.getSessionId(),
            "user", userInfo
        );
    }

    public Map<String, Object> resendOtp(String challengeToken, String ip) {
        if (rateLimitingService.isMfaResendRateLimited(challengeToken, ip)) {
            throw new IllegalStateException("Too many OTP resend attempts. Please try again later.");
        }

        MfaChallenge challenge = getChallenge(challengeToken);
        AuthUserEntity user = authUserRepo.findById(challenge.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String otp = generateOtp();
        challenge.setOtpHash(hashOtp(otp));
        challenge.setAttempts(0);
        challenge.setCreatedAt(System.currentTimeMillis());
        saveChallenge(challengeToken, challenge);

        emailService.sendOtpEmail(user.getEmailId(), otp, challenge.getPurpose().name());
        return Map.of(
            "message", "Verification code resent",
            "email", maskEmail(user.getEmailId())
        );
    }

    @Transactional
    public Map<String, Object> initiateEnableMfa(Long userId, String ip) {
        AuthUserEntity user = authUserRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("Verify your email before enabling MFA");
        }
        if (user.isMfaEnabled()) {
            throw new IllegalStateException("Email MFA is already enabled");
        }

        String challengeToken = createAndSendChallenge(
            user,
            MfaChallenge.Purpose.ENABLE_MFA,
            ip,
            null,
            null
        );

        return Map.of(
            "mfaToken", challengeToken,
            "mfaType", MfaType.EMAIL.name(),
            "email", maskEmail(user.getEmailId()),
            "message", "Enter the code sent to your email to enable MFA"
        );
    }

    @Transactional
    public Map<String, Object> verifyEnableMfa(String challengeToken, String otp) {
        MfaChallenge challenge = getChallenge(challengeToken);
        if (challenge.getPurpose() != MfaChallenge.Purpose.ENABLE_MFA) {
            throw new IllegalArgumentException("Invalid MFA challenge");
        }

        verifyOtp(challengeToken, challenge, otp);

        AuthUserEntity user = authUserRepo.findById(challenge.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setMfaEnabled(true);
        user.setMfaType(MfaType.EMAIL.name());
        authUserRepo.save(user);
        userCacheService.invalidateUserCache(user.getUserId());
        deleteChallenge(challengeToken);

        return Map.of(
            "mfaEnabled", true,
            "mfaType", MfaType.EMAIL.name(),
            "message", "Email MFA enabled successfully"
        );
    }

    @Transactional
    public Map<String, Object> initiateDisableMfa(Long userId, String ip) {
        AuthUserEntity user = authUserRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isMfaEnabled()) {
            throw new IllegalStateException("Email MFA is not enabled");
        }

        String challengeToken = createAndSendChallenge(
            user,
            MfaChallenge.Purpose.DISABLE_MFA,
            ip,
            null,
            null
        );

        return Map.of(
            "mfaToken", challengeToken,
            "message", "Enter the code sent to your email to disable MFA"
        );
    }

    @Transactional
    public Map<String, Object> verifyDisableMfa(String challengeToken, String otp) {
        MfaChallenge challenge = getChallenge(challengeToken);
        if (challenge.getPurpose() != MfaChallenge.Purpose.DISABLE_MFA) {
            throw new IllegalArgumentException("Invalid MFA challenge");
        }

        verifyOtp(challengeToken, challenge, otp);

        AuthUserEntity user = authUserRepo.findById(challenge.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setMfaEnabled(false);
        user.setMfaType(null);
        authUserRepo.save(user);
        userCacheService.invalidateUserCache(user.getUserId());
        deleteChallenge(challengeToken);

        return Map.of(
            "mfaEnabled", false,
            "message", "Email MFA disabled successfully"
        );
    }

    public Map<String, Object> getMfaStatus(Long userId) {
        AuthUserEntity user = authUserRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return Map.of(
            "mfaEnabled", user.isMfaEnabled(),
            "mfaType", user.isMfaEnabled() ? user.getMfaType() : null
        );
    }

    private String createAndSendChallenge(
            AuthUserEntity user,
            MfaChallenge.Purpose purpose,
            String ip,
            String userAgent,
            String deviceId) {

        String challengeToken = UUID.randomUUID().toString();
        String otp = generateOtp();

        MfaChallenge challenge = MfaChallenge.builder()
            .userId(user.getUserId())
            .email(user.getEmailId())
            .username(user.getUsername())
            .otpHash(hashOtp(otp))
            .attempts(0)
            .purpose(purpose)
            .ipAddress(ip)
            .userAgent(userAgent)
            .deviceId(deviceId)
            .createdAt(System.currentTimeMillis())
            .build();

        saveChallenge(challengeToken, challenge);
        emailService.sendOtpEmail(user.getEmailId(), otp, purpose.name());
        return challengeToken;
    }

    private void verifyOtp(String challengeToken, MfaChallenge challenge, String otp) {
        if (rateLimitingService.isMfaVerifyRateLimited(challengeToken)) {
            deleteChallenge(challengeToken);
            throw new IllegalStateException("Too many failed attempts. Please sign in again.");
        }

        if (challenge.getAttempts() >= maxAttempts) {
            deleteChallenge(challengeToken);
            throw new IllegalStateException("Maximum verification attempts exceeded");
        }

        if (!hashOtp(otp).equals(challenge.getOtpHash())) {
            challenge.setAttempts(challenge.getAttempts() + 1);
            saveChallenge(challengeToken, challenge);
            throw new IllegalArgumentException("Invalid verification code");
        }
    }

    private MfaChallenge getChallenge(String challengeToken) {
        String key = CHALLENGE_KEY_PREFIX + challengeToken;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new IllegalArgumentException("MFA challenge expired or invalid");
        }
        try {
            return objectMapper.readValue(json, MfaChallenge.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read MFA challenge");
        }
    }

    private void saveChallenge(String challengeToken, MfaChallenge challenge) {
        try {
            String json = objectMapper.writeValueAsString(challenge);
            redisTemplate.opsForValue().set(
                CHALLENGE_KEY_PREFIX + challengeToken,
                json,
                otpExpiryMinutes,
                TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to store MFA challenge");
        }
    }

    private void deleteChallenge(String challengeToken) {
        redisTemplate.delete(CHALLENGE_KEY_PREFIX + challengeToken);
    }

    private String generateOtp() {
        int code = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", code);
    }

    private String hashOtp(String otp) {
        return DigestUtils.md5DigestAsHex(otp.getBytes(StandardCharsets.UTF_8));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
