package com.example.binanceAuth.binance_auth;

import com.example.binanceAuth.binance_auth.service.TokenBlacklistService;
import com.example.binanceAuth.binance_auth.service.UserCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenBlacklistService tokenBlacklistService;
    private final UserCacheService userCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication instanceof JwtAuthenticationToken) {
                JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
                Jwt jwt = jwtAuth.getToken();
                String token = jwt.getTokenValue();

                // ✅ Check if token is blacklisted in Redis
                if (tokenBlacklistService.isTokenBlacklisted(token)) {
                    log.warn("Blacklisted token attempt: {}", jwt.getSubject());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Token has been revoked\"}");
                    return;
                }

                // ✅ Extract user information from JWT
                Map<String, Object> claims = jwt.getClaims();
                String userId = claims.get("sub").toString();
                String email = claims.get("email") != null ? claims.get("email").toString() : null;
                String username = claims.get("preferred_username") != null ? 
                    claims.get("preferred_username").toString() : null;

                // ✅ Add user context to request attributes
                request.setAttribute("X-User-Id", userId);
                request.setAttribute("X-User-Email", email);
                request.setAttribute("X-Username", username);
                request.setAttribute("X-User-Roles", claims.get("realm_access"));

                log.debug("Authenticated user: {} with ID: {}", username, userId);
            }

        } catch (Exception e) {
            log.warn("Error processing JWT: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}