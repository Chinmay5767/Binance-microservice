package com.example.binanceAuth.binance_auth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;
    private Object metadata;
    private long timestamp;
    private String requestId;

    public static ApiResponse success(String message, Object data) {
        return ApiResponse.builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    public static ApiResponse success(String message, Object data, Object metadata) {
        return ApiResponse.builder()
            .success(true)
            .message(message)
            .data(data)
            .metadata(metadata)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    public static ApiResponse error(String message) {
        return ApiResponse.builder()
            .success(false)
            .message(message)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    public static ApiResponse error(String message, String requestId) {
        return ApiResponse.builder()
            .success(false)
            .message(message)
            .requestId(requestId)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}