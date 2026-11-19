package com.example.binanceAuth.binance_auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@binance.local}")
    private String fromAddress;

    @Value("${app.mfa.email.dev-mode:false}")
    private boolean devMode;

    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        if (devMode) {
            log.info("[DEV MODE] Email OTP for {} ({}): {}", toEmail, purpose, otp);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Your verification code");
        message.setText(buildOtpEmailBody(otp, purpose));
        mailSender.send(message);
        log.info("OTP email sent to {}", toEmail);
    }

    private String buildOtpEmailBody(String otp, String purpose) {
        String action = switch (purpose) {
            case "ENABLE_MFA" -> "enable two-factor authentication on your account";
            case "DISABLE_MFA" -> "disable two-factor authentication on your account";
            default -> "complete your sign-in";
        };

        return """
            Your Binance verification code is: %s

            Use this code to %s.

            This code expires in 10 minutes. Do not share it with anyone.

            If you did not request this code, please ignore this email.
            """.formatted(otp, action);
    }
}
