package com.example.binanceAuth.binance_auth;

import com.example.binanceAuth.binance_auth.service.RateLimitingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    @Value("${app.security.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain)
            throws ServletException, IOException {

        if (!rateLimitingEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIp(request);

        // ✅ Skip rate limiting for OPTIONS and health endpoints
        if ("OPTIONS".equalsIgnoreCase(method) || 
            path.contains("/health") || 
            path.contains("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ Apply different rate limits based on endpoint
        boolean isLimited = false;
        String limitKey = null;
        int limit = 0;

        if (path.contains("/auth/login")) {
            limitKey = "login:" + clientIp;
            isLimited = rateLimitingService.isRateLimited(limitKey, 10, 60);
            limit = 10;
        } else if (path.contains("/auth/register")) {
            limitKey = "register:" + clientIp;
            isLimited = rateLimitingService.isRateLimited(limitKey, 5, 600);
            limit = 5;
        } else if (path.contains("/auth/success") || path.contains("/auth/error")) {
            // OIDC callback - less restrictive
            limitKey = "oidc:" + clientIp;
            isLimited = rateLimitingService.isRateLimited(limitKey, 20, 60);
            limit = 20;
        } else {
            // API requests - rate limit by IP
            limitKey = "api:" + clientIp;
            isLimited = rateLimitingService.isRateLimited(limitKey, 100, 60);
            limit = 100;
        }

        // ✅ Add rate limit headers
        if (limitKey != null) {
            var status = rateLimitingService.getRateLimitStatus(limitKey, limit);
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(status.getRemaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(status.getResetInSeconds()));
        }

        if (isLimited) {
            log.warn("Rate limit exceeded: IP: {}, Path: {}, Method: {}", 
                clientIp, path, method);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"error\":\"Rate limit exceeded\",\"limit\":%d,\"retryAfter\":60}",
                limit
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // For multiple IPs, take the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}