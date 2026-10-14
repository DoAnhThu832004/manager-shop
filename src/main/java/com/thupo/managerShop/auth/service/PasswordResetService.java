package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý luồng quên mật khẩu và đặt lại mật khẩu an toàn.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AuthUserCache authUserCache;
    private final AppSecurityProperties properties;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository passwordResetTokenRepository,
                                PasswordHistoryRepository passwordHistoryRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordEncoder passwordEncoder,
                                PasswordPolicyValidator passwordPolicyValidator,
                                EmailService emailService,
                                AuditService auditService,
                                AuthUserCache authUserCache,
                                AppSecurityProperties properties) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.emailService = emailService;
        this.auditService = auditService;
        this.authUserCache = authUserCache;
        this.properties = properties;
    }

    /**
     * Yêu cầu gửi liên kết đặt lại mật khẩu.
     * Luôn trả kết quả thành công cho client dù email có tồn tại hay không (chống User Enumeration).
     */
    @Transactional
    public void requestPasswordReset(String rawEmail, String clientIp, String userAgent) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return;
        }

        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));

        // 1. Giới hạn số yêu cầu theo IP (tối đa 10 lần/giờ)
        long ipRequestCount = passwordResetTokenRepository.countByRequestedIpAndCreatedAtAfter(clientIp, oneHourAgo);
        if (ipRequestCount >= properties.getPasswordReset().getMaxPerHourPerIp()) {
            log.warn("IP [{}] vượt quá giới hạn yêu cầu đặt lại mật khẩu trong 1 giờ", clientIp);
            return;
        }

        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<User> userOpt = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (userOpt.isEmpty()) {
            // Không tiết lộ email không tồn tại
            log.info("Yêu cầu quên mật khẩu cho email không tồn tại [{}] từ IP [{}]", email, clientIp);
            return;
        }

        User user = userOpt.get();

        // 2. Giới hạn số yêu cầu theo tài khoản (tối đa 3 lần/giờ)
        long userRequestCount = passwordResetTokenRepository.countByUserIdAndCreatedAtAfter(user.getId(), oneHourAgo);
        if (userRequestCount >= properties.getPasswordReset().getMaxPerHourPerEmail()) {
            log.warn("Tài khoản [{}] đã yêu cầu đặt lại mật khẩu quá {} lần trong 1 giờ", email, properties.getPasswordReset().getMaxPerHourPerEmail());
            return;
        }

        // 3. Vô hiệu hoá các token cũ chưa sử dụng
        passwordResetTokenRepository.invalidateUnused(user.getId(), Instant.now());

        // 4. Sinh token ngẫu nhiên bảo mật 32 bytes, chỉ lưu hash SHA-256
        String rawToken = TokenHashUtil.generateRandomToken(32);
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(properties.getPasswordReset().getTtlMinutes()));

        PasswordResetToken resetTokenEntity = new PasswordResetToken();
        resetTokenEntity.setUserId(user.getId());
        resetTokenEntity.setTokenHash(tokenHash);
        resetTokenEntity.setExpiresAt(expiresAt);
        resetTokenEntity.setRequestedIp(clientIp);

        passwordResetTokenRepository.save(resetTokenEntity);

        // 5. Gửi email bất đồng bộ
        emailService.sendPasswordResetLink(user.getEmail(), user.getUsername(), rawToken);

        auditService.record(user.getId(), AuditEventType.PASSWORD_RESET_REQUESTED, AuditSeverity.INFO, clientIp, userAgent, Map.of(
                "email", email
        ));
    }

    /**
     * Đặt lại mật khẩu bằng token.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String clientIp, String userAgent) {
        String tokenHash = TokenHashUtil.sha256Hex(rawToken);

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_RESET_TOKEN_INVALID));

        Instant now = Instant.now();
        if (!token.isUsable(now)) {
            throw new AuthException(ErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_RESET_TOKEN_INVALID));

        // 1. Kiểm tra chính sách mật khẩu
        passwordPolicyValidator.validate(newPassword, user.getUsername(), user.getEmail());

        // 2. Kiểm tra không trùng mật khẩu hiện tại và 3 mật khẩu gần nhất
        checkPasswordHistory(user.getId(), newPassword, user.getPasswordHash());

        // 3. Đánh dấu token đã dùng
        token.setUsedAt(now);
        passwordResetTokenRepository.save(token);

        // 4. Cập nhật mật khẩu mới và mở khoá nếu đang bị khoá
        String newHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newHash);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        userRepository.unlock(user.getId());

        // 5. Lưu vào lịch sử mật khẩu
        savePasswordHistory(user.getId(), newHash);

        // 6. Thu hồi toàn bộ refresh token và vô hiệu access token cũ
        refreshTokenRepository.revokeAllByUserId(user.getId());
        authUserCache.evict(user.getId());

        // 7. Gửi email thông báo và audit
        emailService.sendPasswordChangedAlert(user.getEmail(), user.getUsername(), clientIp);

        auditService.record(user.getId(), AuditEventType.PASSWORD_RESET_COMPLETED, AuditSeverity.INFO, clientIp, userAgent, Map.of(
                "action", "RESET_PASSWORD"
        ));
    }

    private void checkPasswordHistory(Long userId, String newPassword, String currentHash) {
        if (passwordEncoder.matches(newPassword, currentHash)) {
            throw new AuthException(ErrorCode.AUTH_PASSWORD_REUSED);
        }

        List<PasswordHistory> recentHistory = passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId);
        for (PasswordHistory ph : recentHistory) {
            if (passwordEncoder.matches(newPassword, ph.getPasswordHash())) {
                throw new AuthException(ErrorCode.AUTH_PASSWORD_REUSED);
            }
        }
    }

    private void savePasswordHistory(Long userId, String passwordHash) {
        PasswordHistory history = new PasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(passwordHash);
        passwordHistoryRepository.save(history);
    }
}
