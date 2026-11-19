//package com.binance.APIGatewayService;
//
//import java.security.Key;
//import java.util.List;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.core.Ordered;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.server.reactive.ServerHttpRequest;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.security.Keys;
//import lombok.extern.slf4j.Slf4j;
//import reactor.core.publisher.Mono;
//
//@Slf4j
//@Component
//public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
//
//    @Value("${jwt.secret:67d8d84808c94192174972b8c069af2489dcbe398f9f81bbe94547a1fa7f5c35}")
//    private String jwtSecret;
//
//    // Public endpoints that don't require authentication
//    private static final List<String> PUBLIC_ENDPOINTS = List.of(
//            "/api/auth/login",
//            "/api/auth/register",
//            "/api/auth/health",
//            "/api/crypto/stream/prices",
//            "/actuator/health"
//    );
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        ServerHttpRequest request = exchange.getRequest();
//        String path = request.getURI().getPath();
//
//        log.debug("Request received for path: {}", path);
//
//        // Check if endpoint is public
//        if (isPublicEndpoint(path)) {
//            log.debug("Public endpoint accessed: {}", path);
//            return chain.filter(exchange);
//        }
//
//        // Extract JWT token from Authorization header
//        String authHeader = request.getHeaders().getFirst("Authorization");
//        
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            log.warn("Missing or invalid Authorization header for path: {}", path);
//            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
//            return exchange.getResponse().setComplete();
//        }
//
//        String token = authHeader.substring(7);
//        
//        try {
//            // Validate JWT token
//            Claims claims = validateToken(token);
//            log.debug("Token validated for user: {}", claims.getSubject());
//            
//            // Add user context as headers for downstream services
//            ServerHttpRequest mutatedRequest = request.mutate()
//                    .header("X-User-Id", claims.getSubject())
//                    .header("X-Username", claims.get("username", String.class))
//                    .header("X-User-Role", claims.get("role", String.class))
//                    .header("X-User-Email", claims.get("email", String.class))
//                    .build();
//            
//            return chain.filter(exchange.mutate().request(mutatedRequest).build());
//            
//        } catch (Exception e) {
//            log.error("JWT validation failed for path: {}, error: {}", path, e.getMessage());
//            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
//            return exchange.getResponse().setComplete();
//        }
//    }
//
//    private boolean isPublicEndpoint(String path) {
//        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith);
//    }
//
//    private Claims validateToken(String token) {
//        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
//        return Jwts.parserBuilder()
//                .setSigningKey(key)
//                .build()
//                .parseClaimsJws(token)
//                .getBody();
//    }
//
//    @Override
//    public int getOrder() {
//        return -100; // Execute before other filters
//    }
//}
//




package com.binance.APIGatewayService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilterFactory 
        extends AbstractGatewayFilterFactory<JwtAuthenticationFilterFactory.Config> {

    @Value("${jwt.secret:67d8d84808c94192174972b8c069af2489dcbe398f9f81bbe94547a1fa7f5c35}")
    private String jwtSecret;

    public JwtAuthenticationFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new JwtAuthenticationGatewayFilter(config);
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return Arrays.asList("enabled");
    }

    public static class Config {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private class JwtAuthenticationGatewayFilter implements GatewayFilter {

        private final Config config;

        public JwtAuthenticationGatewayFilter(Config config) {
            this.config = config;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            if (!config.isEnabled()) {
                return chain.filter(exchange);
            }

            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // ✅ Extract and validate JWT
            String token = extractToken(request);
            if (token == null) {
                log.warn("Missing JWT token for protected endpoint: {}", path);
                return unauthorizedResponse(exchange, "Missing authentication token");
            }

            try {
                Claims claims = validateToken(token);
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);
                String email = claims.get("email", String.class);
                String roles = claims.get("roles", String.class);

                // ✅ Check if token is expired
                if (claims.getExpiration().before(new java.util.Date())) {
                    log.warn("Token expired for user: {}", username);
                    return unauthorizedResponse(exchange, "Token has expired");
                }

                log.debug("Valid JWT for user: {}, ID: {}", username, userId);

                // ✅ Add user context to headers for downstream services
                ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Username", username != null ? username : "unknown")
                    .header("X-User-Email", email != null ? email : "unknown")
                    .header("X-User-Roles", roles != null ? roles : "ROLE_USER")
                    .header("X-Authenticated", "true")
                    .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());

            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                log.warn("JWT expired: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Token has expired");
            } catch (io.jsonwebtoken.MalformedJwtException e) {
                log.warn("Malformed JWT: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Invalid token format");
            } catch (Exception e) {
                log.error("JWT validation failed: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Invalid or expired token");
            }
        }

        private String extractToken(ServerHttpRequest request) {
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            return null;
        }

        private Claims validateToken(String token) {
            Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        }

        private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(
                org.springframework.http.MediaType.APPLICATION_JSON
            );
            String body = String.format(
                "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":%d}",
                message, System.currentTimeMillis()
            );
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes()))
            );
        }
        
        
    }
   
}