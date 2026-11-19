package com.example.binanceAuth.binance_auth.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaResendRequest {

    @NotBlank
    private String mfaToken;
}
