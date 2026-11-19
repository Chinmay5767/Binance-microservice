package com.example.binanceAuth.binance_auth.service;

import com.example.binanceAuth.binance_auth.entity.AuthUserEntity;
import com.example.binanceAuth.binance_auth.model.AuthUser;
import com.example.binanceAuth.binance_auth.model.DeviceInfo;
import com.example.binanceAuth.binance_auth.repository.AuthUserRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAuthService {

    // Keycloak Configuration
    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.admin.client-id:admin-cli}")
    private String adminClientId;

    @Value("${keycloak.admin.client-secret:}")
    private String adminClientSecret;

    // Dependencies
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthUserRepo authUserRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    // ✅ Authenticate user with Keycloak OIDC
    public Map<String, Object> authenticateUser(String username, String password, String deviceId) {
        log.info("Authenticating user with Keycloak: {}", username);
        
        try {
            // 1. Get tokens from Keycloak
            Map<String, Object> keycloakTokens = getKeycloakTokens(username, password);
            
            // 2. Extract user info from ID token
            Map<String, Object> userInfo = getUserInfoFromKeycloak(
                keycloakTokens.get("access_token").toString()
            );
            
            // 3. Store/Update user in local database
            AuthUserEntity user = syncUserWithKeycloak(userInfo);
            
            // ✅ 4. Generate application tokens - FIXED: using overloaded methods
            String accessToken = tokenService.generateAccessToken(user, deviceId);
            String refreshToken = tokenService.generateRefreshToken(user, deviceId);
            
            // 5. Build response
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("refresh_token", refreshToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", 900); // 15 minutes
            response.put("id_token", keycloakTokens.get("id_token"));
            response.put("user", Map.of(
                "id", user.getUserId(),
                "username", user.getUsername(),
                "email", user.getEmailId(),
                "roles", user.getRoles()
            ));
            
            log.info("Authentication successful for user: {}", username);
            return response;
            
        } catch (HttpClientErrorException e) {
            log.error("Keycloak authentication failed: {}", e.getMessage());
            throw new RuntimeException("Invalid credentials");
        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage(), e);
            throw new RuntimeException("Authentication failed");
        }
    }

    // ✅ Get tokens from Keycloak using Password Grant
    private Map<String, Object> getKeycloakTokens(String username, String password) {
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("grant_type", "password");
        params.add("username", username);
        params.add("password", password);
        params.add("scope", "openid profile email offline_access");
        
        HttpEntity<MultiValueMap<String, String>> request = 
            new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Keycloak token error: {}", e.getMessage());
            throw new RuntimeException("Failed to get tokens from Keycloak");
        }
    }

    // ✅ Get user info from Keycloak
    private Map<String, Object> getUserInfoFromKeycloak(String accessToken) {
        String userInfoUrl = issuerUri + "/protocol/openid-connect/userinfo";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                userInfoUrl,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get user info from Keycloak: {}", e.getMessage());
            throw new RuntimeException("Failed to get user info");
        }
    }

    // ✅ Sync user with local database
    @Transactional
    public AuthUserEntity syncUserWithKeycloak(Map<String, Object> keycloakUser) {
        String email = keycloakUser.get("email").toString();
        String username = keycloakUser.get("preferred_username").toString();
        String firstName = keycloakUser.get("given_name") != null ? 
            keycloakUser.get("given_name").toString() : "";
        String lastName = keycloakUser.get("family_name") != null ? 
            keycloakUser.get("family_name").toString() : "";
        
        // Get roles from Keycloak
        List<String> roles = extractRolesFromKeycloak(keycloakUser);
        
        // Check if user exists
        Optional<AuthUserEntity> existingUser = authUserRepo.findByEmailId(email);
        
        if (existingUser.isPresent()) {
            // Update existing user
            AuthUserEntity user = existingUser.get();
            user.setUsername(username);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setRoles(roles);
            user.setLastLoginAt(LocalDateTime.now());
            user.setEnabled(true);
            
            return authUserRepo.save(user);
        } else {
            // Create new user
            AuthUserEntity newUser = new AuthUserEntity();
            newUser.setEmailId(email);
            newUser.setUsername(username);
            newUser.setFirstName(firstName);
            newUser.setLastName(lastName);
            newUser.setRoles(roles);
            newUser.setEnabled(true);
            newUser.setMfaEnabled(false);
            
            // Generate a random password for local DB
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            
            return authUserRepo.save(newUser);
        }
    }

    // ✅ Extract roles from Keycloak JWT
    private List<String> extractRolesFromKeycloak(Map<String, Object> keycloakUser) {
        List<String> roles = new ArrayList<>();
        
        try {
            // Extract realm roles
            if (keycloakUser.containsKey("realm_access")) {
                Map<String, Object> realmAccess = 
                    (Map<String, Object>) keycloakUser.get("realm_access");
                if (realmAccess.containsKey("roles")) {
                    List<String> realmRoles = (List<String>) realmAccess.get("roles");
                    roles.addAll(realmRoles);
                }
            }
            
            // Extract client roles
            if (keycloakUser.containsKey("resource_access")) {
                Map<String, Object> resourceAccess = 
                    (Map<String, Object>) keycloakUser.get("resource_access");
                if (resourceAccess.containsKey(clientId)) {
                    Map<String, Object> clientAccess = 
                        (Map<String, Object>) resourceAccess.get(clientId);
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
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("Failed to extract roles from Keycloak: {}", e.getMessage());
            return List.of("ROLE_USER");
        }
    }

    // ✅ Register user in Keycloak
    @Transactional
    public Map<String, Object> registerUser(AuthUser authUser) {
        log.info("Registering user in Keycloak: {}", authUser.getEmailId());
        
        try {
            // 1. Create user in Keycloak
            String userId = createKeycloakUser(authUser);
            
            // 2. Set password in Keycloak
            setKeycloakUserPassword(userId, authUser.getPassword());
            
            // 3. Assign default roles
            assignDefaultRoles(userId);
            
            // 4. Create in local database
            AuthUserEntity user = new AuthUserEntity();
            user.setUsername(authUser.getUsername());
            user.setEmailId(authUser.getEmailId());
            user.setPassword(passwordEncoder.encode(authUser.getPassword()));
            user.setRoles(List.of("ROLE_USER"));
            user.setEnabled(true);
            user.setMfaEnabled(false);
            user.setKeycloakId(userId);
            authUserRepo.save(user);
            
            // 5. Send verification email (optional)
            sendVerificationEmail(userId);
            
            log.info("User registered successfully in Keycloak: {}", authUser.getEmailId());
            
            return Map.of(
                "message", "User registered successfully",
                "userId", userId,
                "email", authUser.getEmailId(),
                "username", authUser.getUsername()
            );
            
        } catch (Exception e) {
            log.error("Keycloak registration failed: {}", e.getMessage());
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    // ✅ Create user in Keycloak
    private String createKeycloakUser(AuthUser authUser) {
        String usersUrl = issuerUri + "/users";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAdminAccessToken());
        
        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("username", authUser.getUsername());
        userPayload.put("email", authUser.getEmailId());
        userPayload.put("firstName", "");
        userPayload.put("lastName", "");
        userPayload.put("enabled", true);
        userPayload.put("emailVerified", false);
        
        // Add required actions
        List<Map<String, String>> requiredActions = new ArrayList<>();
        Map<String, String> verifyEmail = new HashMap<>();
        verifyEmail.put("name", "VERIFY_EMAIL");
        requiredActions.add(verifyEmail);
        userPayload.put("requiredActions", requiredActions);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userPayload, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                usersUrl,
                HttpMethod.POST,
                request,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.CREATED) {
                // Extract user ID from Location header
                String location = response.getHeaders().getFirst("Location");
                if (location != null) {
                    return location.substring(location.lastIndexOf("/") + 1);
                }
            }
            
            throw new RuntimeException("Failed to create Keycloak user");
            
        } catch (Exception e) {
            log.error("Keycloak user creation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to create user in Keycloak");
        }
    }

    // ✅ Set password for Keycloak user
    private void setKeycloakUserPassword(String userId, String password) {
        String passwordUrl = issuerUri + "/users/" + userId + "/reset-password";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAdminAccessToken());
        
        Map<String, Object> passwordPayload = new HashMap<>();
        passwordPayload.put("type", "password");
        passwordPayload.put("value", password);
        passwordPayload.put("temporary", false);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(passwordPayload, headers);
        
        try {
            restTemplate.exchange(
                passwordUrl,
                HttpMethod.PUT,
                request,
                String.class
            );
            
            log.debug("Password set for Keycloak user: {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to set password for Keycloak user: {}", e.getMessage());
            throw new RuntimeException("Failed to set password");
        }
    }

    // ✅ Assign default roles to Keycloak user
    private void assignDefaultRoles(String userId) {
        String rolesUrl = issuerUri + "/users/" + userId + "/role-mappings/realm";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAdminAccessToken());
        
        // Get available roles
        List<Map<String, Object>> roles = getAvailableRoles();
        List<Map<String, Object>> userRoles = new ArrayList<>();
        
        // Assign only the 'user' role
        roles.stream()
            .filter(role -> "user".equals(role.get("name")))
            .findFirst()
            .ifPresent(userRoles::add);
        
        HttpEntity<List<Map<String, Object>>> request = 
            new HttpEntity<>(userRoles, headers);
        
        try {
            restTemplate.exchange(
                rolesUrl,
                HttpMethod.POST,
                request,
                String.class
            );
            
            log.debug("Default role assigned to Keycloak user: {}", userId);
            
        } catch (Exception e) {
            log.warn("Failed to assign default role: {}", e.getMessage());
            // Don't throw - roles can be assigned later
        }
    }

    // ✅ Get available roles from Keycloak
    private List<Map<String, Object>> getAvailableRoles() {
        String rolesUrl = issuerUri + "/roles";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAdminAccessToken());
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                rolesUrl,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            log.warn("Failed to get available roles: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ✅ Send verification email
    private void sendVerificationEmail(String userId) {
        String verifyUrl = issuerUri + "/users/" + userId + "/send-verify-email";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAdminAccessToken());
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(
                verifyUrl,
                HttpMethod.PUT,
                request,
                String.class
            );
            
            log.debug("Verification email sent to user: {}", userId);
            
        } catch (Exception e) {
            log.warn("Failed to send verification email: {}", e.getMessage());
        }
    }

    // ✅ Get admin access token (Client Credentials Grant)
    private String getAdminAccessToken() {
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", adminClientId);
        params.add("client_secret", adminClientSecret);
        params.add("grant_type", "client_credentials");
        params.add("scope", "openid");
        
        HttpEntity<MultiValueMap<String, String>> request = 
            new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            return response.getBody().get("access_token").toString();
            
        } catch (Exception e) {
            log.error("Failed to get admin access token: {}", e.getMessage());
            throw new RuntimeException("Failed to get admin token");
        }
    }

    // ✅ Logout from Keycloak
    public void logout(String token) {
        String logoutUrl = issuerUri + "/protocol/openid-connect/logout";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", token);
        
        HttpEntity<MultiValueMap<String, String>> request = 
            new HttpEntity<>(params, headers);
        
        try {
            restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                request,
                String.class
            );
            
            log.debug("Logout successful in Keycloak");
            
        } catch (Exception e) {
            log.warn("Keycloak logout failed: {}", e.getMessage());
        }
    }

    // ✅ Logout from all devices
    public void logoutAllDevices(String userId) {
        String logoutUrl = issuerUri + "/users/" + userId + "/logout";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAdminAccessToken());
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            restTemplate.exchange(
                logoutUrl,
                HttpMethod.POST,
                request,
                String.class
            );
            
            log.info("Logged out all devices for user: {}", userId);
            
        } catch (Exception e) {
            log.warn("Failed to logout all devices: {}", e.getMessage());
        }
    }

    // ✅ Refresh token with Keycloak
    public Map<String, Object> refreshToken(String refreshToken, String deviceId) {
        log.debug("Refreshing token with Keycloak");
        
        String tokenUrl = issuerUri + "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("grant_type", "refresh_token");
        params.add("refresh_token", refreshToken);
        
        HttpEntity<MultiValueMap<String, String>> request = 
            new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> tokens = response.getBody();
            
            // Get user email from ID token
            String idToken = tokens.get("id_token").toString();
            String email = extractEmailFromIdToken(idToken);
            
            // Update user in local DB
            AuthUserEntity user = authUserRepo.findByEmailId(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // ✅ Generate new application tokens - FIXED
            String newAccessToken = tokenService.generateAccessToken(user, deviceId);
            String newRefreshToken = tokenService.generateRefreshToken(user, deviceId);
            
            tokens.put("access_token", newAccessToken);
            tokens.put("refresh_token", newRefreshToken);
            
            return tokens;
            
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            throw new RuntimeException("Refresh token failed");
        }
    }

    // ✅ Extract email from ID token (JWT)
    private String extractEmailFromIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length == 3) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JsonNode jsonNode = objectMapper.readTree(payload);
                return jsonNode.get("email").asText();
            }
        } catch (Exception e) {
            log.error("Failed to extract email from ID token: {}", e.getMessage());
        }
        return null;
    }

    // ✅ Get user info from Authentication
    public Map<String, Object> getUserInfo(Authentication authentication) {
        Map<String, Object> userInfo = new HashMap<>();
        
        try {
            if (authentication instanceof JwtAuthenticationToken) {
                JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
                Jwt jwt = jwtAuth.getToken();
                Map<String, Object> claims = jwt.getClaims();
                
                userInfo.put("sub", claims.get("sub"));
                userInfo.put("email", claims.get("email"));
                userInfo.put("preferred_username", claims.get("preferred_username"));
                userInfo.put("name", claims.get("name"));
                userInfo.put("roles", claims.get("realm_access"));
                
                // Get authorities
                Collection<GrantedAuthority> authorities = jwtAuth.getAuthorities();
                userInfo.put("authorities", authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
            }
            
            if (authentication instanceof OidcUser) {
                OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
                userInfo.putAll(oidcUser.getClaims());
                userInfo.put("email", oidcUser.getEmail());
                userInfo.put("username", oidcUser.getPreferredUsername());
                userInfo.put("name", oidcUser.getFullName());
            }
            
        } catch (Exception e) {
            log.error("Failed to get user info: {}", e.getMessage());
        }
        
        return userInfo;
    }

    // ✅ Check Keycloak health
    public boolean isKeycloakHealthy() {
        try {
            String healthUrl = issuerUri.replace("/realms", "/health");
            ResponseEntity<String> response = restTemplate.getForEntity(
                healthUrl, String.class
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            return false;
        }
    }

    // ✅ Complete login after MFA verification
    public Map<String, Object> completeLogin(String username, String deviceId) {
        log.info("Completing login after MFA for: {}", username);
        
        AuthUserEntity user = authUserRepo.findByEmailId(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // ✅ Generate tokens - FIXED: using overloaded methods
        String accessToken = tokenService.generateAccessToken(user, deviceId);
        String refreshToken = tokenService.generateRefreshToken(user, deviceId);
        
        return Map.of(
            "access_token", accessToken,
            "refresh_token", refreshToken,
            "token_type", "Bearer",
            "expires_in", 900,
            "user", Map.of(
                "id", user.getUserId(),
                "username", user.getUsername(),
                "email", user.getEmailId(),
                "roles", user.getRoles()
            )
        );
    }
}