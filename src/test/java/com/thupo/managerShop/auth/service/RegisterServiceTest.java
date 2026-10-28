package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.dto.RegisterRequest;
import com.thupo.managerShop.auth.dto.RegisterResponse;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.RoleName;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.ratelimit.RateLimitStore;
import com.thupo.managerShop.auth.repository.RoleRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;
    @Mock
    private AuditService auditService;
    @Mock
    private RateLimitStore rateLimitStore;

    private AppSecurityProperties properties;
    private RegisterService registerService;

    @BeforeEach
    void setUp() {
        properties = new AppSecurityProperties();
        properties.getRegistration().setEnabled(true);
        properties.getRegistration().setDefaultRole(RoleName.CASHIER);
        properties.getRateLimit().setRegisterPerMinutePerIp(5);

        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }
            @Override
            public void commit(TransactionStatus status) {}
            @Override
            public void rollback(TransactionStatus status) {}
        };
        TransactionTemplate transactionTemplate = new TransactionTemplate(txManager);

        registerService = new RegisterService(
                userRepository,
                roleRepository,
                passwordEncoder,
                passwordPolicyValidator,
                auditService,
                rateLimitStore,
                properties,
                transactionTemplate
        );
    }

    @Test
    @DisplayName("Đăng ký thành công -> Gán vai trò an toàn CASHIER, hash pass ngoài transaction, ghi audit REGISTER_SUCCESS")
    void shouldRegisterSuccessfully() {
        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A",
                "nguyenvana",
                "vana@example.com",
                "Password123@",
                "Password123@"
        );
        String clientIp = "192.168.1.10";
        String userAgent = "Mozilla/5.0";

        when(rateLimitStore.tryAcquire(eq("register:ip:" + clientIp), eq(5), any(Duration.class))).thenReturn(true);
        when(userRepository.existsByUsername("nguyenvana")).thenReturn(false);
        when(userRepository.existsByEmail("vana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123@")).thenReturn("$2a$12$hashedPasswordVal");

        Role cashierRole = new Role();
        cashierRole.setName(RoleName.CASHIER);
        when(roleRepository.findByName(RoleName.CASHIER)).thenReturn(Optional.of(cashierRole));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(100L);
            return user;
        });

        RegisterResponse response = registerService.register(request, clientIp, userAgent);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("nguyenvana", response.username());
        assertEquals("vana@example.com", response.email());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertTrue(response.roles().contains("CASHIER"));

        verify(passwordPolicyValidator).validate("Password123@", "nguyenvana", "vana@example.com");
        verify(auditService).record(eq(100L), eq(AuditEventType.REGISTER_SUCCESS), eq(AuditSeverity.INFO),
                eq(clientIp), eq(userAgent), any());
    }

    @Test
    @DisplayName("Bị chặn khi tính năng đăng ký công khai bị vô hiệu hoá")
    void shouldThrowWhenRegistrationDisabled() {
        properties.getRegistration().setEnabled(false);

        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A", "nguyenvana", "vana@example.com", "Password123@", "Password123@"
        );

        AuthException ex = assertThrows(AuthException.class,
                () -> registerService.register(request, "127.0.0.1", "curl"));
        assertEquals(ErrorCode.AUTH_REGISTRATION_DISABLED, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bị chặn khi vượt ngưỡng Rate Limit IP")
    void shouldThrowWhenRateLimitExceeded() {
        String clientIp = "10.0.0.1";
        when(rateLimitStore.tryAcquire(eq("register:ip:" + clientIp), eq(5), any(Duration.class))).thenReturn(false);

        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A", "nguyenvana", "vana@example.com", "Password123@", "Password123@"
        );

        AuthException ex = assertThrows(AuthException.class,
                () -> registerService.register(request, clientIp, "browser"));
        assertEquals(ErrorCode.AUTH_RATE_LIMITED, ex.getErrorCode());
        assertEquals(60L, ex.getRetryAfterSeconds());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lỗi khi mật khẩu xác nhận không khớp -> Chặn ngay ở RAM, không tốn token RateLimit")
    void shouldThrowWhenPasswordConfirmationMismatched() {
        String clientIp = "127.0.0.1";

        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A", "nguyenvana", "vana@example.com", "Password123@", "DifferentPass123@"
        );

        AuthException ex = assertThrows(AuthException.class,
                () -> registerService.register(request, clientIp, "browser"));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(rateLimitStore, never()).tryAcquire(anyString(), any(int.class), any(Duration.class));
        verify(passwordPolicyValidator, never()).validate(anyString(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đăng ký thành công với trạng thái PENDING_APPROVAL nếu cấu hình yêu cầu duyệt")
    void shouldRegisterWithPendingApprovalStatus() {
        properties.getRegistration().setInitialStatus(UserStatus.PENDING_APPROVAL);

        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A", "nguyenvana", "vana@example.com", "Password123@", "Password123@"
        );
        String clientIp = "192.168.1.10";

        when(rateLimitStore.tryAcquire(anyString(), eq(5), any(Duration.class))).thenReturn(true);
        when(userRepository.existsByUsername("nguyenvana")).thenReturn(false);
        when(userRepository.existsByEmail("vana@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        Role cashierRole = new Role();
        cashierRole.setName(RoleName.CASHIER);
        when(roleRepository.findByName(RoleName.CASHIER)).thenReturn(Optional.of(cashierRole));

        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(200L);
            return u;
        });

        RegisterResponse response = registerService.register(request, clientIp, "browser");
        assertEquals(UserStatus.PENDING_APPROVAL, response.status());
        assertTrue(response.message().contains("chờ quản trị viên duyệt"));
    }

    @Test
    @DisplayName("Lỗi khi tên đăng nhập đã tồn tại")
    void shouldThrowWhenUsernameExists() {
        String clientIp = "127.0.0.1";
        when(rateLimitStore.tryAcquire(anyString(), eq(5), any(Duration.class))).thenReturn(true);
        when(userRepository.existsByUsername("adminuser")).thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A", "adminuser", "newemail@example.com", "Password123@", "Password123@"
        );

        AuthException ex = assertThrows(AuthException.class,
                () -> registerService.register(request, clientIp, "browser"));
        assertEquals(ErrorCode.AUTH_USER_ALREADY_EXISTS, ex.getErrorCode());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lỗi khi email đã được đăng ký")
    void shouldThrowWhenEmailExists() {
        String clientIp = "127.0.0.1";
        when(rateLimitStore.tryAcquire(anyString(), eq(5), any(Duration.class))).thenReturn(true);
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("exist@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A", "newuser", "exist@example.com", "Password123@", "Password123@"
        );

        AuthException ex = assertThrows(AuthException.class,
                () -> registerService.register(request, clientIp, "browser"));
        assertEquals(ErrorCode.AUTH_USER_ALREADY_EXISTS, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xử lý an toàn khi gặp Race Condition (DataIntegrityViolationException)")
    void shouldHandleRaceConditionGracefully() {
        String clientIp = "127.0.0.1";
        when(rateLimitStore.tryAcquire(anyString(), eq(5), any(Duration.class))).thenReturn(true);
        when(userRepository.existsByUsername("concurrent_user")).thenReturn(false);
        when(userRepository.existsByEmail("concurrent@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pass");

        Role cashierRole = new Role();
        cashierRole.setName(RoleName.CASHIER);
        when(roleRepository.findByName(RoleName.CASHIER)).thenReturn(Optional.of(cashierRole));

        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate key entry"));

        RegisterRequest request = new RegisterRequest(
                "Nguyen Van A", "concurrent_user", "concurrent@example.com", "Password123@", "Password123@"
        );

        AuthException ex = assertThrows(AuthException.class,
                () -> registerService.register(request, clientIp, "browser"));
        assertEquals(ErrorCode.AUTH_USER_ALREADY_EXISTS, ex.getErrorCode());
    }
}
