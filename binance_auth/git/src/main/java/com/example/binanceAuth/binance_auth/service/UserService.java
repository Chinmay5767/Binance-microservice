package com.example.binanceAuth.binance_auth.service;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.model.UserInfo;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AuthUserRepo authUserRepository;

    // ═══════════════════════════════════════════════════════════════
    // 1. FIND USER METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Find user by ID
     */
    public AuthUserEntity getUserById(Long userId) {
        return authUserRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
    }

    /**
     * Find user by ID (returns UserInfo)
     */
    public UserInfo getUserInfoById(Long userId) {
        AuthUserEntity user = getUserById(userId);
        return convertToUserInfo(user);
    }

    /**
     * Find user by email
     */
    public AuthUserEntity getUserByEmail(String email) {
        return authUserRepository.findByEmailId(email)
            .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
    }

    /**
     * Find user by Keycloak ID
     */
    public AuthUserEntity getUserByKeycloakId(String keycloakId) {
        return authUserRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new RuntimeException("User not found with Keycloak ID: " + keycloakId));
    }

    /**
     * Find user by username
     */
    public AuthUserEntity getUserByUsername(String username) {
        return authUserRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
    }

    /**
     * Check if user exists
     */
    public boolean userExists(String email) {
        return authUserRepository.existsByEmailId(email);
    }

    /**
     * Get user entity (returns null if not found)
     */
    public AuthUserEntity getUserEntity(Long userId) {
        return authUserRepository.findById(userId).orElse(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. SYNC USER FROM KEYCLOAK
    // ═══════════════════════════════════════════════════════════════

    /**
     * Sync user from Keycloak OIDC to local database
     */
    @Transactional
    public UserInfo syncUser(OidcUser oidcUser) {
        log.info("Syncing user from Keycloak: {}", oidcUser.getEmail());

        String email = oidcUser.getEmail();
        String keycloakId = oidcUser.getSubject();
        String username = oidcUser.getPreferredUsername();

        // ✅ Check if user exists
        Optional<AuthUserEntity> existingUser = authUserRepository.findByEmailId(email);

        if (existingUser.isPresent()) {
            // ✅ Update existing user
            AuthUserEntity user = existingUser.get();
            user.setUsername(username);
            user.setFirstName(oidcUser.getGivenName());
            user.setLastName(oidcUser.getFamilyName());
            user.setEmailVerified(oidcUser.getEmailVerified());
            user.setKeycloakId(keycloakId);
            user.setLastLoginAt(LocalDateTime.now());
            user.setEnabled(true);

            // ✅ Update roles from Keycloak
            List<String> roles = extractRoles(oidcUser);
            user.setRoles(roles);

            AuthUserEntity updatedUser = authUserRepository.save(user);
            log.info("User updated in local DB: {}", email);

            return convertToUserInfo(updatedUser);
        } else {
            // ✅ Create new user
            AuthUserEntity newUser = new AuthUserEntity();
            newUser.setEmailId(email);
            newUser.setUsername(username);
            newUser.setKeycloakId(keycloakId);
            newUser.setFirstName(oidcUser.getGivenName());
            newUser.setLastName(oidcUser.getFamilyName());
            newUser.setEmailVerified(oidcUser.getEmailVerified());
            newUser.setEnabled(true);

            // ✅ Set default roles
            List<String> roles = extractRoles(oidcUser);
            newUser.setRoles(roles);

            // ✅ Set timestamps
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setLastLoginAt(LocalDateTime.now());

            AuthUserEntity savedUser = authUserRepository.save(newUser);
            log.info("User created in local DB: {}", email);

            return convertToUserInfo(savedUser);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. USER REGISTRATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Register new user
     */
    @Transactional
    public AuthUserEntity registerUser(String username, String email, String password, List<String> roles) {
        log.info("Registering new user: {}", email);

        // ✅ Check if user already exists
        if (authUserRepository.existsByEmailId(email)) {
            throw new RuntimeException("Email already registered: " + email);
        }

        if (authUserRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already taken: " + username);
        }

        // ✅ Create new user
        AuthUserEntity user = new AuthUserEntity();
        user.setUsername(username);
        user.setEmailId(email);
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setRoles(roles != null ? roles : List.of("ROLE_USER"));

        AuthUserEntity savedUser = authUserRepository.save(user);
        log.info("User registered successfully: {}", email);

        return savedUser;
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. UPDATE USER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Update user information
     */
    @Transactional
    public AuthUserEntity updateUser(Long userId, String firstName, String lastName, String username) {
        AuthUserEntity user = getUserById(userId);

        if (firstName != null) user.setFirstName(firstName);
        if (lastName != null) user.setLastName(lastName);
        if (username != null) {
            // Check if username is taken
            if (!user.getUsername().equals(username) && authUserRepository.existsByUsername(username)) {
                throw new RuntimeException("Username already taken: " + username);
            }
            user.setUsername(username);
        }

        return authUserRepository.save(user);
    }

    /**
     * Update user roles
     */
    @Transactional
    public AuthUserEntity updateUserRoles(Long userId, List<String> roles) {
        AuthUserEntity user = getUserById(userId);
        user.setRoles(roles);
        return authUserRepository.save(user);
    }

    /**
     * Verify user email
     */
    @Transactional
    public void verifyEmail(Long userId) {
        AuthUserEntity user = getUserById(userId);
        user.setEmailVerified(true);
        authUserRepository.save(user);
        log.info("Email verified for user: {}", user.getEmailId());
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. USER STATUS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Enable/disable user account
     */
    @Transactional
    public void setUserEnabled(Long userId, boolean enabled) {
        AuthUserEntity user = getUserById(userId);
        user.setEnabled(enabled);
        authUserRepository.save(user);
        log.info("User {} enabled: {}", user.getEmailId(), enabled);
    }

    /**
     * Track login time
     */
    @Transactional
    public void trackLogin(Long userId) {
        AuthUserEntity user = getUserById(userId);
        user.setLastLoginAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        authUserRepository.save(user);
    }

    /**
     * Increment failed login attempts
     */
    @Transactional
    public void incrementFailedLoginAttempts(Long userId) {
        AuthUserEntity user = getUserById(userId);
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        
        // Lock account after 5 failed attempts
        if (user.getFailedLoginAttempts() >= 5) {
            user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(30));
            log.warn("Account locked for user: {} due to failed login attempts", user.getEmailId());
        }
        
        authUserRepository.save(user);
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. DELETE USER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Delete user
     */
    @Transactional
    public void deleteUser(Long userId) {
        AuthUserEntity user = getUserById(userId);
        authUserRepository.delete(user);
        log.info("User deleted: {}", user.getEmailId());
    }

    /**
     * Soft delete user (disable only)
     */
    @Transactional
    public void softDeleteUser(Long userId) {
        setUserEnabled(userId, false);
        log.info("User soft deleted: {}", userId);
    }

    // ═══════════════════════════════════════════════════════════════
    // 7. UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Convert AuthUserEntity to UserInfo
     */
    public UserInfo convertToUserInfo(AuthUserEntity user) {
        if (user == null) {
            return null;
        }

        return UserInfo.builder()
            .id(String.valueOf(user.getUserId()))
            .username(user.getUsername())
            .email(user.getEmailId())
            .fullName((user.getFirstName() != null && user.getLastName() != null) 
                ? user.getFirstName() + " " + user.getLastName() 
                : user.getUsername())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .roles(user.getRoles())
            .emailVerified(user.isEmailVerified())
            .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0)
            .lastLoginAt(user.getLastLoginAt() != null ? user.getLastLoginAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0)
            .build();
    }

    /**
     * Extract roles from Keycloak OIDC user
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(OidcUser oidcUser) {
        List<String> roles = new ArrayList<>();
        
        try {
            Map<String, Object> claims = oidcUser.getClaims();
            
            // Get realm roles
            if (claims.containsKey("realm_access")) {
                Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
                if (realmAccess.containsKey("roles")) {
                    List<String> realmRoles = (List<String>) realmAccess.get("roles");
                    roles.addAll(realmRoles);
                }
            }
            
            // Get client roles
            if (claims.containsKey("resource_access")) {
                Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
                if (resourceAccess.containsKey("crypto-app")) {
                    Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("crypto-app");
                    if (clientAccess.containsKey("roles")) {
                        List<String> clientRoles = (List<String>) clientAccess.get("roles");
                        roles.addAll(clientRoles);
                    }
                }
            }
            
            // Add default role if no roles
            if (roles.isEmpty()) {
                roles.add("ROLE_USER");
            }
            
            // Prefix roles with ROLE_ if not already
            return roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .toList();
                
        } catch (Exception e) {
            log.warn("Failed to extract roles from Keycloak: {}", e.getMessage());
            return List.of("ROLE_USER");
        }
    }

    /**
     * Get all users (admin)
     */
    public List<UserInfo> getAllUsers() {
        return authUserRepository.findAll().stream()
            .map(this::convertToUserInfo)
            .toList();
    }

    /**
     * Get user count
     */
    public long getUserCount() {
        return authUserRepository.count();
    }

    /**
     * Get active user count
     */
    public long getActiveUserCount() {
        return authUserRepository.findAll().stream()
            .filter(AuthUserEntity::isEnabled)
            .count();
    }
}