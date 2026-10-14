package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý việc kiểm tra và xử lý khoá tài khoản luỹ tiến khi phát hiện brute-force.
 */
@Service
public class AccountLockService {

    private static final Logger log = LoggerFactory.getLogger(AccountLockService.class);

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppSecurityProperties properties;

    public AccountLockService(UserRepository userRepository,
                              EmailService emailService,
                              AuditService auditService,
                              AppSecurityProperties properties) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Kiểm tra xem tài khoản có đang trong thời gian bị khoá tạm thời hay không.
     * Nếu bị khoá, ném AuthException(AUTH_ACCOUNT_LOCKED) kèm số giây còn lại.
     */
    public void checkAccountLocked(User user) {
        Instant now = Instant.now();
        if (user.isTemporarilyLocked(now)) {
            long remainingSeconds = Duration.between(now, user.getLockUntil()).toSeconds();
            long retryAfter = Math.max(1, remainingSeconds);
            throw new AuthException(ErrorCode.AUTH_ACCOUNT_LOCKED, retryAfter);
        }
    }

    /**
     * Xử lý khi đăng nhập sai mật khẩu.
     * Cập nhật bộ đếm nguyên tử trong DB. Nếu vượt quá ngưỡng -> khoá luỹ tiến.
     * Dùng transaction riêng (REQUIRES_NEW) để bộ đếm luôn được lưu kể cả khi caller ném exception.
     *
     * @return số lần sai hiện tại sau khi tăng
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int handleFailedLogin(User user, String ip, String userAgent) {
        // Tăng bộ đếm nguyên tử trong MySQL
        userRepository.incrementFailedCount(user.getId());
        int currentFailedCount = userRepository.findFailedLoginCount(user.getId()).orElse(1);

        int maxAttempts = properties.getLogin().getMaxFailedAttempts();
        if (currentFailedCount >= maxAttempts) {
            applyProgressiveLock(user, ip, userAgent);
        }

        return currentFailedCount;
    }

    /**
     * Xử lý khi đăng nhập thành công: reset bộ đếm thất bại về 0.
     */
    @Transactional
    public void handleSuccessfulLogin(User user) {
        if (user.getFailedLoginCount() > 0) {
            userRepository.resetFailedCount(user.getId());
        }
    }

    /**
     * Admin mở khoá tài khoản.
     */
    @Transactional
    public void unlockUser(Long userId, String adminUsername, String ip, String userAgent) {
        userRepository.unlock(userId);
        auditService.record(userId, AuditEventType.ACCOUNT_UNLOCKED, AuditSeverity.INFO, ip, userAgent, Map.of(
                "unlockedBy", adminUsername
        ));
        log.info("Admin [{}] đã mở khoá tài khoản [userId={}]", adminUsername, userId);
    }

    private void applyProgressiveLock(User user, String ip, String userAgent) {
        int nextLevel = user.getLockLevel() + 1;
        List<Integer> durations = properties.getLogin().getLockDurationsMinutes();
        int durationMinutes = nextLevel <= durations.size()
                ? durations.get(nextLevel - 1)
                : durations.get(durations.size() - 1); // Cấp độ cao nhất

        Instant lockUntil = Instant.now().plus(Duration.ofMinutes(durationMinutes));
        userRepository.applyLock(user.getId(), lockUntil);

        // Gửi email cảnh báo bất đồng bộ
        emailService.sendAccountLockedAlert(user.getEmail(), user.getUsername(), lockUntil, ip);

        // Ghi nhật ký kiểm toán bảo mật
        auditService.record(user.getId(), AuditEventType.ACCOUNT_LOCKED, AuditSeverity.HIGH, ip, userAgent, Map.of(
                "lockLevel", nextLevel,
                "durationMinutes", durationMinutes,
                "lockUntil", lockUntil.toString()
        ));

        log.warn("Tài khoản [{}] đã bị khoá tạm thời trong {} phút (cấp độ {}) từ IP [{}]",
                user.getUsername(), durationMinutes, nextLevel, ip);
    }
}
