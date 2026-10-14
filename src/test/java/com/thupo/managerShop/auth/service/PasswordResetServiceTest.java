package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.PasswordHistory;
import com.thupo.managerShop.auth.entity.PasswordResetToken;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.repository.PasswordHistoryRepository;
import com.thupo.managerShop.auth.repository.PasswordResetTokenRepository;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.AuthUserCache;
import com.thupo.managerShop.auth.security.TokenHashUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordHistoryRepository passwordHistoryRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;
    @Mock
    private AuthUserCache authUserCache;

    private AppSecurityProperties properties;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getPasswordReset().setTtlMinutes(15);
        properties.getPasswordReset().setMaxPerHourPerEmail(3);
        properties.getPasswordReset().setMaxPerHourPerIp(10);
        properties.getPasswordReset().setHistorySize(3);

        passwordResetService = new PasswordResetService(
                userRepository,
                passwordResetTokenRepository,
                passwordHistoryRepository,
                refreshTokenRepository,
                passwordEncoder,
                passwordPolicyValidator,
                emailService,
                auditService,
                authUserCache,
                properties
        );
    }

    @Test
    @DisplayName("Yêu cầu quên mật khẩu: email không tồn tại thì không làm gì (chống User Enumeration)")
    void shouldSilentlyHandleNonExistentEmail() {
        String email = "notfound@shop.com";
        when(passwordResetTokenRepository.countByRequestedIpAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset(email, "1.2.3.4", "Browser");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetLink(any(), any(), any());
    }

    @Test
    @DisplayName("Yêu cầu quên mật khẩu: vượt quá 3 lần/giờ/email thì không gửi thêm")
    void shouldRateLimitPasswordResetPerEmail() {
        String email = "user@shop.com";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);

        when(passwordResetTokenRepository.countByRequestedIpAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        // Đã có 3 lần yêu cầu trong 1 giờ
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(1L), any())).thenReturn(3L);

        passwordResetService.requestPasswordReset(email, "1.2.3.4", "Browser");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetLink(any(), any(), any());
    }

    @Test
    @DisplayName("Yêu cầu quên mật khẩu hợp lệ: vô hiệu token cũ, lưu hash SHA-256 của token mới và gửi email")
    void shouldGenerateTokenAndSendEmail() {
        String email = "valid@shop.com";
        User user = new User();
        user.setId(2L);
        user.setUsername("validuser");
        user.setEmail(email);

        when(passwordResetTokenRepository.countByRequestedIpAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(userRepository.findByEmailAndDeletedAtIsNull(email)).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(eq(2L), any())).thenReturn(0L);

        passwordResetService.requestPasswordReset(email, "203.0.113.1", "Browser");

        // Kiểm tra vô hiệu các token cũ
        verify(passwordResetTokenRepository).invalidateUnused(eq(2L), any());

        // Kiểm tra lưu token mới (chỉ lưu hash)
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(2L, savedToken.getUserId());
        assertNotNull(savedToken.getTokenHash());
        assertEquals(64, savedToken.getTokenHash().length()); // SHA-256 hex 64 ký tự

        // Kiểm tra gửi email
        verify(emailService).sendPasswordResetLink(eq(email), eq("validuser"), anyString());
    }

    @Test
    @DisplayName("Đặt lại mật khẩu: token hết hạn hoặc đã sử dụng -> ném AUTH_RESET_TOKEN_INVALID (410)")
    void shouldThrowWhenResetTokenExpiredOrUsed() {
        String rawToken = "my_reset_token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        PasswordResetToken expiredToken = new PasswordResetToken();
        expiredToken.setUserId(3L);
        expiredToken.setTokenHash(tokenHash);
        expiredToken.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1))); // Hết hạn

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredToken));

        AuthException exception = assertThrows(AuthException.class,
                () -> passwordResetService.resetPassword(rawToken, "NewPass123", "1.2.3.4", "Browser"));

        assertEquals(ErrorCode.AUTH_RESET_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("Đặt lại mật khẩu: trùng mật khẩu hiện tại hoặc 3 mật khẩu gần nhất -> ném AUTH_PASSWORD_REUSED")
    void shouldThrowWhenPasswordReused() {
        String rawToken = "valid_reset_token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);
        String newPassword = "PasswordAlreadyUsed1";

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(4L);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));

        User user = new User();
        user.setId(4L);
        user.setUsername("user4");
        user.setPasswordHash("$2a$12$currentHash");

        PasswordHistory history = new PasswordHistory();
        history.setPasswordHash("$2a$12$historicHash");

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(newPassword, "$2a$12$currentHash")).thenReturn(false);
        when(passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(4L)).thenReturn(List.of(history));
        when(passwordEncoder.matches(newPassword, "$2a$12$historicHash")).thenReturn(true); // Trùng mật khẩu lịch sử

        AuthException exception = assertThrows(AuthException.class,
                () -> passwordResetService.resetPassword(rawToken, newPassword, "1.2.3.4", "Browser"));

        assertEquals(ErrorCode.AUTH_PASSWORD_REUSED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Đặt lại mật khẩu thành công: mở khoá tài khoản, thu hồi mọi token, tăng token_version")
    void shouldResetPasswordSuccessfully() {
        String rawToken = "success_token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);
        String newPassword = "BrandNewSecurePassword123";

        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(5L);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));

        User user = new User();
        user.setId(5L);
        user.setUsername("user5");
        user.setEmail("user5@shop.com");
        user.setPasswordHash("$2a$12$oldHash");
        user.setTokenVersion(1);

        when(passwordResetTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(eq(newPassword), anyString())).thenReturn(false);
        when(passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of());
        when(passwordEncoder.encode(newPassword)).thenReturn("$2a$12$newlyEncodedHash");

        passwordResetService.resetPassword(rawToken, newPassword, "203.0.113.8", "Browser");

        // Kiểm tra token đã được đánh dấu used
        assertNotNull(token.getUsedAt());
        verify(passwordResetTokenRepository).save(token);

        // Kiểm tra mật khẩu mới và tăng token_version
        assertEquals("$2a$12$newlyEncodedHash", user.getPasswordHash());
        assertEquals(2, user.getTokenVersion());
        verify(userRepository).save(user);

        // Kiểm tra mở khoá tài khoản nếu đang bị khoá
        verify(userRepository).unlock(5L);

        // Kiểm tra lưu lịch sử mật khẩu
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));

        // Kiểm tra thu hồi toàn bộ refresh token
        verify(refreshTokenRepository).revokeAllByUserId(5L);
        verify(authUserCache).evict(5L);

        // Kiểm tra gửi email thông báo
        verify(emailService).sendPasswordChangedAlert("user5@shop.com", "user5", "203.0.113.8");
    }
}
