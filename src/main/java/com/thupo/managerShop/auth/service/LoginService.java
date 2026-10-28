package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.captcha.CaptchaVerifier;
import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.config.AsyncConfig;
import com.thupo.managerShop.auth.dto.LoginRequest;
import com.thupo.managerShop.auth.dto.LoginResponse;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.RefreshToken;
import com.thupo.managerShop.auth.entity.RefreshTokenStatus;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.ratelimit.RateLimitStore;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.JwtService;
import com.thupo.managerShop.auth.security.TokenHashUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service xử lý đăng nhập production-grade với đầy đủ các cơ chế phòng thủ:
 * - Rate limit trước khi băm mật khẩu để bảo vệ CPU
 * - Chống User Enumeration và Timing Attack bằng Dummy Hash
 * - Khoá tài khoản luỹ tiến và chặn IP tự động
 * - Rotation Refresh token và giới hạn số phiên đồng thời
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimitStore rateLimitStore;
    private final AccountLockService accountLockService;
    private final IpBlockService ipBlockService;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final CaptchaVerifier captchaVerifier;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppSecurityProperties properties;
    private final TransactionTemplate transactionTemplate;

    // Dummy hash được sinh sẵn lúc khởi động để thực hiện so sánh giả lập khi user không tồn tại
    private final String dummyPasswordHash;

    @Autowired
    public LoginService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        RateLimitStore rateLimitStore,
                        AccountLockService accountLockService,
                        IpBlockService ipBlockService,
                        LoginAttemptRecorder loginAttemptRecorder,
                        CaptchaVerifier captchaVerifier,
                        EmailService emailService,
                        AuditService auditService,
                        AppSecurityProperties properties,
                        TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimitStore = rateLimitStore;
        this.accountLockService = accountLockService;
        this.ipBlockService = ipBlockService;
        this.loginAttemptRecorder = loginAttemptRecorder;
        this.captchaVerifier = captchaVerifier;
        this.emailService = emailService;
        this.auditService = auditService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        // Băm một chuỗi ngẫu nhiên bằng đúng độ mạnh BCrypt để cân bằng thời gian phản hồi
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Luồng xử lý đăng nhập chính.
     * Tách tính toán CPU-bound (BCrypt) ra ngoài Transaction để tránh ngâm connection HikariCP.
     */
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        String identifier = request.identifier().trim().toLowerCase(Locale.ROOT);
        // 1. Rate limit endpoint login theo IP & tài khoản (chạy trước khi chạm DB và BCrypt để chống DoS CPU)
        checkRateLimit(clientIp, identifier);

        Optional<User> userOpt = userRepository.findByIdentifierWithRoles(identifier);

        // 2. Chống User Enumeration & Timing Attack khi tài khoản không tồn tại
        if (userOpt.isEmpty()) {
            performDummyHash(request.password());
            recordUnknownUserFailure(identifier, clientIp, userAgent);
            throw new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        User user = userOpt.get();

        // 3. Kiểm tra trạng thái tài khoản
        if (user.getStatus() == UserStatus.PENDING_APPROVAL) {
            loginAttemptRecorder.record(user.getUsername(), clientIp, false, "ACCOUNT_PENDING_APPROVAL", userAgent);
            throw new AuthException(ErrorCode.AUTH_ACCOUNT_PENDING_APPROVAL);
        }
        if (user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            loginAttemptRecorder.record(user.getUsername(), clientIp, false, "ACCOUNT_DISABLED", userAgent);
            throw new AuthException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        // 4. Kiểm tra khoá tài khoản tạm thời
        accountLockService.checkAccountLocked(user);

        // 5. Kiểm tra Captcha nếu tài khoản đã thử sai nhiều lần
        int captchaThreshold = properties.getLogin().getCaptchaAfterFailures();
        boolean captchaRequired = user.getFailedLoginCount() >= captchaThreshold;
        if (captchaRequired) {
            if (!captchaVerifier.verify(request.captchaToken(), clientIp)) {
                throw new AuthException(ErrorCode.VALIDATION_ERROR, "Vui lòng xác thực Captcha trước khi tiếp tục", null, true);
            }
        }

        // 6. Kiểm tra mật khẩu (CPU-bound, hoàn toàn không giữ JDBC Connection!)
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!passwordMatches) {
            int failedCount = handleFailedPassword(user, clientIp, userAgent);
            // Tính toán xem lần kế tiếp có cần captcha không
            boolean nextCaptchaRequired = failedCount >= captchaThreshold;
            throw new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS, ErrorCode.AUTH_INVALID_CREDENTIALS.getDefaultMessage(), null, nextCaptchaRequired);
        }

        // 7. Thực thi transaction phạm vi siêu ngắn (~3-5ms) qua TransactionTemplate
        LoginResponse response = transactionTemplate.execute(status -> {
            accountLockService.handleSuccessfulLogin(user);
            enforceMaxSessions(user.getId());

            List<Long> branchIds = userRepository.findAccessibleBranchIdsByUserId(user.getId());
            String accessToken = jwtService.generateAccessToken(user, branchIds);
            String rawRefreshToken = TokenHashUtil.generateRandomToken(32);
            String refreshTokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);

            Instant now = Instant.now();
            Instant refreshExpiresAt = now.plus(Duration.ofDays(properties.getJwt().getRefreshTtlDays()));

            RefreshToken refreshTokenEntity = new RefreshToken();
            refreshTokenEntity.setUserId(user.getId());
            refreshTokenEntity.setTokenHash(refreshTokenHash);
            refreshTokenEntity.setFamilyId(UUID.randomUUID().toString());
            refreshTokenEntity.setStatus(RefreshTokenStatus.ACTIVE);
            refreshTokenEntity.setExpiresAt(refreshExpiresAt);
            refreshTokenEntity.setDeviceInfo(request.deviceInfo());
            refreshTokenEntity.setIpAddress(clientIp);
            refreshTokenEntity.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);

            refreshTokenRepository.save(refreshTokenEntity);

            // Cập nhật last login vào DB
            userRepository.touchLastLogin(user.getId(), now, clientIp);

            List<String> roleNames = user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList();
            Long branchId = user.getBranch() != null ? user.getBranch().getId() : null;
            String branchName = user.getBranch() != null ? user.getBranch().getName() : null;

            return LoginResponse.of(
                    accessToken,
                    rawRefreshToken,
                    jwtService.getAccessTtlSeconds(),
                    roleNames,
                    branchId,
                    branchName,
                    false
            );
        });

        // 8. Ghi nhận log attempt và audit sau khi commit transaction chính
        loginAttemptRecorder.record(user.getUsername(), clientIp, true, null, userAgent);
        auditService.record(user.getId(), AuditEventType.LOGIN_SUCCESS, AuditSeverity.INFO, clientIp, userAgent, Map.of(
                "deviceInfo", request.deviceInfo() != null ? request.deviceInfo() : "unknown"
        ));

        // Phát hiện đăng nhập từ IP mới
        if (user.getLastLoginIp() != null && !user.getLastLoginIp().equals(clientIp)) {
            emailService.sendNewDeviceLoginAlert(user.getEmail(), user.getUsername(), clientIp, request.deviceInfo());
        }

        return response;
    }

    private void checkRateLimit(String clientIp, String identifier) {
        int limit = properties.getRateLimit().getLoginPerMinutePerIp();
        boolean allowed = rateLimitStore.tryAcquire("login:ip:" + clientIp, limit, Duration.ofMinutes(1));
        if (!allowed) {
            throw new AuthException(ErrorCode.AUTH_RATE_LIMITED, 60L);
        }

        int userLimit = properties.getRateLimit().getLoginPerMinutePerUser();
        if (userLimit > 0 && identifier != null && !identifier.isBlank()) {
            boolean allowedUser = rateLimitStore.tryAcquire("login:user:" + clientIp + ":" + identifier, userLimit, Duration.ofMinutes(1));
            if (!allowedUser) {
                throw new AuthException(ErrorCode.AUTH_RATE_LIMITED, 60L);
            }
        }
    }

    private void performDummyHash(String rawPassword) {
        // Thực hiện so sánh BCrypt giả để làm giả lập độ trễ tương đương với user thật
        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }

    private void recordUnknownUserFailure(String identifier, String clientIp, String userAgent) {
        loginAttemptRecorder.record(identifier, clientIp, false, "USER_NOT_FOUND", userAgent);
        ipBlockService.recordFailedAttempt(clientIp, identifier, userAgent);
    }

    private int handleFailedPassword(User user, String clientIp, String userAgent) {
        int failedCount = accountLockService.handleFailedLogin(user, clientIp, userAgent);
        ipBlockService.recordFailedAttempt(clientIp, user.getUsername(), userAgent);
        loginAttemptRecorder.record(user.getUsername(), clientIp, false, "INVALID_PASSWORD", userAgent);
        auditService.record(user.getId(), AuditEventType.LOGIN_FAILED, AuditSeverity.WARN, clientIp, userAgent, Map.of(
                "reason", "Sai mật khẩu"
        ));
        if (failedCount >= properties.getLogin().getMaxFailedAttempts()) {
            List<Integer> durations = properties.getLogin().getLockDurationsMinutes();
            int nextLevel = user.getLockLevel() + 1;
            int durationMinutes = nextLevel <= durations.size()
                    ? durations.get(nextLevel - 1)
                    : durations.get(durations.size() - 1);
            throw new AuthException(ErrorCode.AUTH_ACCOUNT_LOCKED, durationMinutes * 60L);
        }
        return failedCount;
    }

    private void enforceMaxSessions(Long userId) {
        int maxSessions = properties.getLogin().getMaxSessionsPerUser();
        List<RefreshToken> activeSessions = refreshTokenRepository
                .findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(userId, RefreshTokenStatus.ACTIVE, Instant.now());

        if (activeSessions.size() >= maxSessions) {
            // Thu hồi các phiên cũ nhất để chừa chỗ cho phiên mới
            int sessionsToRevoke = activeSessions.size() - maxSessions + 1;
            for (int i = 0; i < sessionsToRevoke; i++) {
                RefreshToken oldSession = activeSessions.get(i);
                oldSession.setStatus(RefreshTokenStatus.REVOKED);
                refreshTokenRepository.save(oldSession);
                log.info("Thu hồi phiên đăng nhập cũ [id={}] của user [{}] do vượt giới hạn phiên", oldSession.getId(), userId);
            }
        }
    }
}
