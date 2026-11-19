package com.example.binanceAuth.binance_auth.controller;

import com.example.binanceAuth.binance_auth.model.ApiResponse;
import com.example.binanceAuth.binance_auth.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * ✅ Check session status (Called by other microservices)
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<ApiResponse> getSessionStatus(@PathVariable String sessionId) {
        try {
            var session = sessionService.getSession(sessionId);
            
            if (session == null) {
                return ResponseEntity.status(401)
                    .body(ApiResponse.error("Session expired or invalid"));
            }
            
            if (!session.isActive()) {
                return ResponseEntity.status(401)
                    .body(ApiResponse.error("Session is inactive"));
            }
            
            // Update last activity
            sessionService.updateSessionActivity(sessionId);
            
            return ResponseEntity.ok(ApiResponse.success("Session active", session));
            
        } catch (Exception e) {
            log.error("Failed to get session status: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to get session status"));
        }
    }

    /**
     * ✅ Validate session (Called by API Gateway)
     */
    @GetMapping("/validate/{sessionId}")
    public ResponseEntity<ApiResponse> validateSession(@PathVariable String sessionId) {
        try {
            boolean isValid = sessionService.isSessionActive(sessionId);
            
            if (isValid) {
                sessionService.updateSessionActivity(sessionId);
                return ResponseEntity.ok(ApiResponse.success("Session valid", 
                    Map.of("valid", true)));
            }
            
            return ResponseEntity.status(401)
                .body(ApiResponse.error("Session invalid"));
            
        } catch (Exception e) {
            log.error("Failed to validate session: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to validate session"));
        }
    }
}