package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.dto.LoginResponse;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.RefreshToken;
import com.thupo.managerShop.auth.entity.RefreshTokenStatus;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.RoleName;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.AuthUserCache;
import com.thupo.managerShop.auth.security.JwtService;
import com.thupo.managerShop.auth.security.TokenHashUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthUserCache authUserCache;
    @Mock
    private AuditService auditService;

    private AppSecurityProperties properties;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getJwt().setRefreshTtlDays(7);
        properties.getJwt().setRefreshGraceSeconds(5);

        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                jwtService,
                authUserCache,
                auditService,
                properties
        );
    }

    @Test
    @DisplayName("Token không tồn tại trong DB -> ném lỗi AUTH_TOKEN_INVALID (401)")
    void shouldThrowWhenTokenNotFound() {
        when(refreshTokenRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class,
                () -> refreshTokenService.refresh("non_existent_raw_token", "1.2.3.4", "Agent"));

        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("Token đã hết hạn -> ném lỗi AUTH_TOKEN_EXPIRED (401)")
    void shouldThrowWhenTokenExpired() {
        String rawToken = "sample_raw_token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.setUserId(1L);
        token.setTokenHash(tokenHash);
        token.setStatus(RefreshTokenStatus.ACTIVE);
        token.setExpiresAt(Instant.now().minus(Duration.ofDays(1))); // Hết hạn ngày hôm qua

        User user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.ACTIVE);

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));

        AuthException exception = assertThrows(AuthException.class,
                () -> refreshTokenService.refresh(rawToken, "1.2.3.4", "Agent"));

        assertEquals(ErrorCode.AUTH_TOKEN_EXPIRED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Rotation bình thường: token cũ thành USED, sinh token mới cùng familyId và access token mới")
    void shouldRotateTokenSuccessfully() {
        String rawToken = "valid_raw_token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        RefreshToken oldToken = new RefreshToken();
        oldToken.setId(10L);
        oldToken.setUserId(2L);
        oldToken.setTokenHash(tokenHash);
        oldToken.setFamilyId("family-uuid-123");
        oldToken.setStatus(RefreshTokenStatus.ACTIVE);
        oldToken.setExpiresAt(Instant.now().plus(Duration.ofDays(6)));
        oldToken.setDeviceInfo("Desktop-Chrome");

        Role role = new Role();
        role.setName(RoleName.CASHIER);
        User user = new User();
        user.setId(2L);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(oldToken));
        when(userRepository.findByIdWithRoles(2L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("new.access.token");
        when(jwtService.getAccessTtlSeconds()).thenReturn(900L);

        LoginResponse response = refreshTokenService.refresh(rawToken, "203.0.113.10", "Chrome/120");

        assertNotNull(response);
        assertEquals("new.access.token", response.accessToken());
        assertNotNull(response.refreshToken());

        // Kiểm tra 2 lần save: 1 lần lưu token cũ chuyển USED, 1 lần lưu token mới ACTIVE
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        List<RefreshToken> savedTokens = captor.getAllValues();
        RefreshToken updatedOldToken = savedTokens.get(0);
        RefreshToken newlyCreatedToken = savedTokens.get(1);

        assertEquals(RefreshTokenStatus.USED, updatedOldToken.getStatus());
        assertNotNull(updatedOldToken.getUsedAt());

        assertEquals("family-uuid-123", newlyCreatedToken.getFamilyId());
        assertEquals(RefreshTokenStatus.ACTIVE, newlyCreatedToken.getStatus());
    }

    @Test
    @DisplayName("Reuse Detection: Gửi lại token đã USED sau Grace Period -> Thu hồi cả họ token và ném AUTH_REFRESH_REUSED")
    void shouldDetectTokenReuseAndRevokeFamily() {
        String leakedToken = "stolen_used_token";
        String tokenHash = TokenHashUtil.sha256Hex(leakedToken);

        RefreshToken usedToken = new RefreshToken();
        usedToken.setUserId(3L);
        usedToken.setFamilyId("stolen-family-456");
        usedToken.setStatus(RefreshTokenStatus.USED);
        // Đã sử dụng từ 30 giây trước (vượt quá 5s grace period)
        usedToken.setUsedAt(Instant.now().minusSeconds(30));
        usedToken.setExpiresAt(Instant.now().plus(Duration.ofDays(5)));

        User user = new User();
        user.setId(3L);
        user.setStatus(UserStatus.ACTIVE);

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(usedToken));
        when(userRepository.findByIdWithRoles(3L)).thenReturn(Optional.of(user));

        AuthException exception = assertThrows(AuthException.class,
                () -> refreshTokenService.refresh(leakedToken, "198.51.100.99", "HackerBot/1.0"));

        assertEquals(ErrorCode.AUTH_REFRESH_REUSED, exception.getErrorCode());

        // Xác minh toàn bộ token family đã bị thu hồi
        verify(refreshTokenRepository).revokeFamily("stolen-family-456");
        // Xác minh ghi audit log mức độ HIGH
        verify(auditService).record(eq(3L), eq(AuditEventType.REFRESH_TOKEN_REUSE_DETECTED),
                eq(AuditSeverity.HIGH), eq("198.51.100.99"), eq("HackerBot/1.0"), any());
    }

    @Test
    @DisplayName("Đăng xuất phiên hiện tại: thu hồi toàn bộ token family tương ứng")
    void shouldLogoutSession() {
        String rawToken = "logout_raw_token";
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        RefreshToken token = new RefreshToken();
        token.setUserId(5L);
        token.setFamilyId("logout-family-789");

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        refreshTokenService.logout(rawToken, 5L, "10.0.0.1", "Browser");

        verify(refreshTokenRepository).revokeFamily("logout-family-789");
    }

    @Test
    @DisplayName("Đăng xuất toàn bộ thiết bị: thu hồi mọi token, tăng token_version và evict cache")
    void shouldLogoutAllDevices() {
        Long userId = 9L;

        refreshTokenService.logoutAll(userId, "10.0.0.1", "Browser");

        verify(refreshTokenRepository).revokeAllByUserId(userId);
        verify(userRepository).bumpTokenVersion(userId);
        verify(authUserCache).evict(userId);
    }
}
