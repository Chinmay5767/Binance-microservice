package com.example.binanceAuth.binance_auth.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession implements Serializable {
    private String sessionId;
    private String userId;
    private String email;
    private String username;
    private String userAgent;
    private String ipAddress;
    private String deviceId;
    private long createdAt;
    private long lastActivityAt;
    private long expiresAt;
    private boolean isActive;
    private List<String> roles;
}