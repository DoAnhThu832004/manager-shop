package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.captcha.CaptchaVerifier;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.dto.LoginRequest;
import com.thupo.managerShop.auth.dto.LoginResponse;
import com.thupo.managerShop.auth.entity.RefreshToken;
import com.thupo.managerShop.auth.entity.RefreshTokenStatus;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.RoleName;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.ratelimit.RateLimitStore;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.JwtService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RateLimitStore rateLimitStore;
    @Mock
    private AccountLockService accountLockService;
    @Mock
    private IpBlockService ipBlockService;
    @Mock
    private LoginAttemptRecorder loginAttemptRecorder;
    @Mock
    private CaptchaVerifier captchaVerifier;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    private AppSecurityProperties properties;
    private LoginService loginService;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getRateLimit().setLoginPerMinutePerIp(10);
        properties.getLogin().setMaxSessionsPerUser(5);
        properties.getLogin().setCaptchaAfterFailures(3);
        properties.getJwt().setRefreshTtlDays(7);

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$dummyHashValueForTestingTimingAttacks");

        loginService = new LoginService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                rateLimitStore,
                accountLockService,
                ipBlockService,
                loginAttemptRecorder,
                captchaVerifier,
                emailService,
                auditService,
                properties
        );
    }

    @Test
    @DisplayName("IP bị rate limit quá số request/phút -> ném lỗi AUTH_RATE_LIMITED (429)")
    void shouldThrowRateLimitedException() {
        String clientIp = "203.0.113.1";
        LoginRequest request = new LoginRequest("user1", "Password123", "POS-1", null);

        when(rateLimitStore.tryAcquire(eq("login:ip:" + clientIp), eq(10), any(Duration.class))).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class,
                () -> loginService.login(request, clientIp, "Mozilla/5.0"));

        assertEquals(ErrorCode.AUTH_RATE_LIMITED, exception.getErrorCode());
        verify(userRepository, never()).findByIdentifierWithRoles(anyString());
    }

    @Test
    @DisplayName("User không tồn tại: chạy dummy BCrypt hash và trả về mã lỗi chung AUTH_INVALID_CREDENTIALS")
    void shouldPerformDummyHashWhenUserNotFound() {
        String clientIp = "203.0.113.2";
        LoginRequest request = new LoginRequest("unknown_user", "Password123", "POS-1", null);

        when(rateLimitStore.tryAcquire(anyString(), eq(10), any(Duration.class))).thenReturn(true);
        when(userRepository.findByIdentifierWithRoles("unknown_user")).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class,
                () -> loginService.login(request, clientIp, "Mozilla/5.0"));

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, exception.getErrorCode());
        // Xác minh dummy hash được gọi để cân bằng thời gian phản hồi
        verify(passwordEncoder).matches(eq("Password123"), anyString());
        verify(ipBlockService).recordFailedAttempt(clientIp, "unknown_user", "Mozilla/5.0");
    }

    @Test
    @DisplayName("Tài khoản bị vô hiệu hoá (DISABLED) -> ném lỗi AUTH_ACCOUNT_DISABLED (403)")
    void shouldThrowWhenAccountDisabled() {
        String clientIp = "203.0.113.3";
        LoginRequest request = new LoginRequest("disabled_user", "Password123", "POS-1", null);

        User disabledUser = new User();
        disabledUser.setUsername("disabled_user");
        disabledUser.setStatus(UserStatus.DISABLED);

        when(rateLimitStore.tryAcquire(anyString(), eq(10), any(Duration.class))).thenReturn(true);
        when(userRepository.findByIdentifierWithRoles("disabled_user")).thenReturn(Optional.of(disabledUser));

        AuthException exception = assertThrows(AuthException.class,
                () -> loginService.login(request, clientIp, "Mozilla/5.0"));

        assertEquals(ErrorCode.AUTH_ACCOUNT_DISABLED, exception.getErrorCode());
        verify(passwordEncoder, never()).matches(eq("Password123"), any());
    }

    @Test
    @DisplayName("Tài khoản đang bị khoá tạm thời -> ném lỗi AUTH_ACCOUNT_LOCKED (423)")
    void shouldThrowWhenAccountTemporarilyLocked() {
        String clientIp = "203.0.113.4";
        LoginRequest request = new LoginRequest("locked_user", "Password123", "POS-1", null);

        User lockedUser = new User();
        lockedUser.setUsername("locked_user");
        lockedUser.setStatus(UserStatus.ACTIVE);

        when(rateLimitStore.tryAcquire(anyString(), eq(10), any(Duration.class))).thenReturn(true);
        when(userRepository.findByIdentifierWithRoles("locked_user")).thenReturn(Optional.of(lockedUser));
        doThrow(new AuthException(ErrorCode.AUTH_ACCOUNT_LOCKED, 900L))
                .when(accountLockService).checkAccountLocked(lockedUser);

        AuthException exception = assertThrows(AuthException.class,
                () -> loginService.login(request, clientIp, "Mozilla/5.0"));

        assertEquals(ErrorCode.AUTH_ACCOUNT_LOCKED, exception.getErrorCode());
        assertEquals(900L, exception.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Sai mật khẩu: ghi nhận thất bại, tăng bộ đếm và trả mã lỗi AUTH_INVALID_CREDENTIALS")
    void shouldHandleFailedPassword() {
        String clientIp = "203.0.113.5";
        LoginRequest request = new LoginRequest("cashier1", "WrongPassword", "POS-1", null);

        User user = new User();
        user.setId(10L);
        user.setUsername("cashier1");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("$2a$12$actualHashInDatabase");

        when(rateLimitStore.tryAcquire(anyString(), eq(10), any(Duration.class))).thenReturn(true);
        when(userRepository.findByIdentifierWithRoles("cashier1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$actualHashInDatabase")).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class,
                () -> loginService.login(request, clientIp, "Mozilla/5.0"));

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, exception.getErrorCode());
        verify(accountLockService).handleFailedLogin(user, clientIp, "Mozilla/5.0");
        verify(ipBlockService).recordFailedAttempt(clientIp, "cashier1", "Mozilla/5.0");
    }

    @Test
    @DisplayName("Đăng nhập thành công: reset bộ đếm, thu hồi phiên cũ nếu vượt quá 5, sinh token")
    void shouldLoginSuccessfully() {
        String clientIp = "203.0.113.6";
        LoginRequest request = new LoginRequest("manager1", "CorrectPassword123", "POS-Branch-1", null);

        Role role = new Role();
        role.setName(RoleName.MANAGER);

        User user = new User();
        user.setId(1L);
        user.setUsername("manager1");
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("$2a$12$validPasswordHash");
        user.setRoles(Set.of(role));

        when(rateLimitStore.tryAcquire(anyString(), eq(10), any(Duration.class))).thenReturn(true);
        when(userRepository.findByIdentifierWithRoles("manager1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("CorrectPassword123", "$2a$12$validPasswordHash")).thenReturn(true);

        // Giả lập người dùng đã có 5 phiên đang mở -> Phải thu hồi 1 phiên cũ nhất
        List<RefreshToken> existingSessions = new ArrayList<>();
        for (long i = 1; i <= 5; i++) {
            RefreshToken session = new RefreshToken();
            session.setId(i);
            session.setStatus(RefreshTokenStatus.ACTIVE);
            existingSessions.add(session);
        }
        when(refreshTokenRepository.findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(
                eq(1L), eq(RefreshTokenStatus.ACTIVE), any(Instant.class))).thenReturn(existingSessions);

        when(jwtService.generateAccessToken(user)).thenReturn("jwt.access.token");
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);

        LoginResponse response = loginService.login(request, clientIp, "POS-Client/1.0");

        assertNotNull(response);
        assertEquals("jwt.access.token", response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals(900L, response.expiresIn());
        assertEquals(List.of("MANAGER"), response.roles());

        // Kiểm tra reset số lần sai
        verify(accountLockService).handleSuccessfulLogin(user);
        // Kiểm tra phiên cũ nhất (id=1) đã bị chuyển thành REVOKED
        assertEquals(RefreshTokenStatus.REVOKED, existingSessions.get(0).getStatus());
        verify(refreshTokenRepository).save(existingSessions.get(0));
        // Kiểm tra tổng cộng 2 lần gọi save (1 lần thu hồi phiên cũ, 1 lần lưu phiên mới)
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(any(RefreshToken.class));
    }
}
