package com.example.binanceAuth.binance_auth.controller;

import com.example.binanceAuth.binance_auth.model.ApiResponse;
import com.example.binanceAuth.binance_auth.model.MfaResendRequest;
import com.example.binanceAuth.binance_auth.model.MfaVerifyRequest;
import com.example.binanceAuth.binance_auth.service.MfaService;
import com.example.binanceAuth.binance_auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth/mfa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;
    private final TokenService tokenService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse> verifyLoginOtp(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest httpRequest) {

        try {
            Map<String, Object> result = mfaService.verifyLoginOtp(
                request.getMfaToken(),
                request.getOtp(),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(ApiResponse.success("MFA verification successful", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("MFA verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("MFA verification failed"));
        }
    }

    @PostMapping("/resend")
    public ResponseEntity<ApiResponse> resendOtp(
            @Valid @RequestBody MfaResendRequest request,
            HttpServletRequest httpRequest) {

        try {
            Map<String, Object> result = mfaService.resendOtp(
                request.getMfaToken(),
                httpRequest.getRemoteAddr()
            );
            return ResponseEntity.ok(ApiResponse.success("OTP resent", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("OTP resend failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to resend OTP"));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse> getMfaStatus(
            @RequestHeader("Authorization") String authHeader) {

        try {
            Long userId = extractUserId(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
            }
            return ResponseEntity.ok(ApiResponse.success(
                "MFA status retrieved",
                mfaService.getMfaStatus(userId)
            ));
        } catch (Exception e) {
            log.error("Failed to get MFA status: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to get MFA status"));
        }
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse> initiateEnableMfa(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        try {
            Long userId = extractUserId(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
            }
            Map<String, Object> result = mfaService.initiateEnableMfa(userId, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(ApiResponse.success("Verification code sent", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to initiate MFA enable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to initiate MFA enable"));
        }
    }

    @PostMapping("/enable/verify")
    public ResponseEntity<ApiResponse> verifyEnableMfa(
            @Valid @RequestBody MfaVerifyRequest request) {

        try {
            Map<String, Object> result = mfaService.verifyEnableMfa(
                request.getMfaToken(),
                request.getOtp()
            );
            return ResponseEntity.ok(ApiResponse.success("Email MFA enabled", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to verify MFA enable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to enable MFA"));
        }
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse> initiateDisableMfa(
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        try {
            Long userId = extractUserId(authHeader);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
            }
            Map<String, Object> result = mfaService.initiateDisableMfa(userId, httpRequest.getRemoteAddr());
            return ResponseEntity.ok(ApiResponse.success("Verification code sent", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to initiate MFA disable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to initiate MFA disable"));
        }
    }

    @PostMapping("/disable/verify")
    public ResponseEntity<ApiResponse> verifyDisableMfa(
            @Valid @RequestBody MfaVerifyRequest request) {

        try {
            Map<String, Object> result = mfaService.verifyDisableMfa(
                request.getMfaToken(),
                request.getOtp()
            );
            return ResponseEntity.ok(ApiResponse.success("Email MFA disabled", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to verify MFA disable: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Failed to disable MFA"));
        }
    }

    private Long extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        String userIdStr = tokenService.extractUserId(token);
        return userIdStr != null ? Long.parseLong(userIdStr) : null;
    }
}
