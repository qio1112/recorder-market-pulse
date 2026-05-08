package com.yipeng.recorder.service;

import com.yipeng.recorder.exception.InvalidRequestException;
import com.yipeng.recorder.model.PasswordResetToken;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.repository.PasswordResetTokenRepository;
import com.yipeng.recorder.repository.UserRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SendEmailService sendEmailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.env:PROD}")
    private String appEnv;

    @Value("${app.frontend.dev-url}")
    private String devFrontendUrl;

    @Value("${app.frontend.prod-url}")
    private String prodFrontendUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            SendEmailService sendEmailService) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.sendEmailService = sendEmailService;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            logger.info("Password reset requested with empty email.");
            return;
        }
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            logger.info("Password reset requested for unknown email.");
            return;
        }

        User user = userOptional.get();
        invalidateExistingTokens(user);

        String rawToken = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setCreatedAt(ZonedDateTime.now());
        resetToken.setExpiresAt(ZonedDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
        passwordResetTokenRepository.save(resetToken);

        sendResetEmailAsync(user.getEmail(), user.getUsername(), rawToken);
        logger.info("Password reset email queued for user: {}", user.getUsername());
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRequestException("Password reset link is invalid or expired.");
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8) {
            throw new InvalidRequestException("New password must be at least 8 characters long.");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashAndUsedAtIsNull(hashToken(rawToken))
                .orElseThrow(() -> new InvalidRequestException("Password reset link is invalid or expired."));

        if (resetToken.getExpiresAt().isBefore(ZonedDateTime.now())) {
            resetToken.setUsedAt(ZonedDateTime.now());
            passwordResetTokenRepository.save(resetToken);
            throw new InvalidRequestException("Password reset link is invalid or expired.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsedAt(ZonedDateTime.now());
        passwordResetTokenRepository.save(resetToken);
        invalidateExistingTokens(user);
        logger.info("Password reset completed for user: {}", user.getUsername());
    }

    private void invalidateExistingTokens(User user) {
        for (PasswordResetToken token : passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)) {
            token.setUsedAt(ZonedDateTime.now());
            passwordResetTokenRepository.save(token);
        }
    }

    private void sendResetEmailAsync(String email, String username, String rawToken) {
        String resetLink = getFrontendUrl().replaceAll("/+$", "") + "/reset-password?token=" + rawToken;
        String text = "Use this link to reset your Recorder password. The link expires in "
                + TOKEN_EXPIRY_MINUTES + " minutes.\n\n"
                + resetLink + "\n\n"
                + "If you did not request a password reset, you can ignore this email.";
        CompletableFuture.runAsync(() -> {
            try {
                sendEmailService.sendEmail(email, "Reset Recorder Password", text, null);
                logger.info("Password reset email sent for user: {}", username);
            } catch (MessagingException e) {
                logger.error("Failed to send password reset email for user: {}", username, e);
            }
        });
    }

    private String getFrontendUrl() {
        return "DEV".equalsIgnoreCase(appEnv) ? devFrontendUrl : prodFrontendUrl;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
