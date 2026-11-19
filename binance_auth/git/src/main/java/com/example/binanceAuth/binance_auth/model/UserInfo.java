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
public class UserInfo implements Serializable {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String firstName;
    private String lastName;
    private List<String> roles;
    private boolean emailVerified;
    private long createdAt;
    private long lastLoginAt;
}