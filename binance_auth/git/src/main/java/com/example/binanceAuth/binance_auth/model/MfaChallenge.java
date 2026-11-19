package com.example.binanceAuth.binance_auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaChallenge implements Serializable {

    public enum Purpose {
        LOGIN,
        ENABLE_MFA,
        DISABLE_MFA
    }

    private Long userId;
    private String email;
    private String username;
    private String otpHash;
    private int attempts;
    private Purpose purpose;
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    private long createdAt;
}
