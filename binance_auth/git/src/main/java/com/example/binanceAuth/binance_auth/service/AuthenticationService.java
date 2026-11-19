package com.example.binanceAuth.binance_auth.service;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.model.AuthUser;
import com.example.binanceAuth.binance_auth.model.DeviceInfo;
import com.example.binanceAuth.binance_auth.model.LoginResult;
import com.example.binanceAuth.binance_auth.model.MfaType;
import com.example.binanceAuth.binance_auth.model.UserRegistrationResult;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    @Autowired
    private final AuthUserRepo authUserRepo;
    private final PasswordEncoder passwordEncoder;
    @Autowired
    private final TokenService tokenService;
    @Autowired
    private final KeycloakAuthService keycloakAuthService;
    @Autowired
    private final MfaService mfaService;

    // ✅ Authenticate user with email and password
    public LoginResult authenticate(String email, String password) {
        log.info("Authenticating user: {}", email);
        
        // 1. Authenticate with Spring Security
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        );
        
        // 2. Get user from database
        AuthUserEntity user = authUserRepo.findByEmailId(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        DeviceInfo deviceInfo = DeviceInfo.builder().deviceId("device-id").build();
        deviceInfo.setDeviceId("generated-device-id");

        if (user.isMfaEnabled()) {
            Map<String, Object> challenge = mfaService.initiateLoginChallenge(
                user,
                null,
                null,
                deviceInfo.getDeviceId()
            );
            return LoginResult.mfaRequired(
                user,
                challenge.get("mfaToken").toString(),
                MfaType.EMAIL
            );
        }

        return LoginResult.builder()
            .user(user)
            .deviceInfo(deviceInfo)
            .authenticated(true)
            .mfaRequired(false)
            .build();
    }

    // ✅ Register new user
    @Transactional
    public UserRegistrationResult register(String username, String email, String password, String role) {
        log.info("Registering user: {}", email);
        
        // 1. Check if user exists
        if (authUserRepo.findByEmailId(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        if (authUserRepo.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username already taken");
        }
        
        // 2. Create new user
        AuthUserEntity user = new AuthUserEntity();
        user.setUsername(username);
        user.setEmailId(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoles(List.of(role != null ? role : "ROLE_USER"));
        user.setEnabled(true);
        user.setMfaEnabled(false);
        
        // 3. Save user
        AuthUserEntity savedUser = authUserRepo.save(user);
   
       // 4. Optionally create in keykloak
       // keycloakAuthService.createUser(email, username, password);
        
        return UserRegistrationResult.builder()
            .userId(savedUser.getUserId())
            .email(savedUser.getEmailId())
            .username(savedUser.getUsername())
            .build();
    }

    // ✅ Logout user
    public void logout(String token, Long userId, String deviceId) {
        tokenService.blacklistToken(token, userId, deviceId);
        keycloakAuthService.logout(token);
    }

    // ✅ Get user info
    public Map<String, Object> getUserInfo(Authentication authentication) {
        return keycloakAuthService.getUserInfo(authentication);
    }
}