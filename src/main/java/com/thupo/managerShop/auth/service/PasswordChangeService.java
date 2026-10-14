package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.PasswordHistory;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.repository.PasswordHistoryRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.AuthUserCache;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý đổi mật khẩu cho người dùng đã đăng nhập.
 */
@Service
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AuthUserCache authUserCache;

    public PasswordChangeService(UserRepository userRepository,
                                 PasswordHistoryRepository passwordHistoryRepository,
                                 PasswordEncoder passwordEncoder,
                                 PasswordPolicyValidator passwordPolicyValidator,
                                 EmailService emailService,
                                 AuditService auditService,
                                 AuthUserCache authUserCache) {
        this.userRepository = userRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.emailService = emailService;
        this.auditService = auditService;
        this.authUserCache = authUserCache;
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String clientIp, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin tài khoản", null));

        // 1. Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Mật khẩu hiện tại không chính xác", null);
        }

        // 2. Kiểm tra chính sách mật khẩu mới
        passwordPolicyValidator.validate(newPassword, user.getUsername(), user.getEmail());

        // 3. Kiểm tra không trùng mật khẩu hiện tại và 3 mật khẩu gần nhất
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new AuthException(ErrorCode.AUTH_PASSWORD_REUSED);
        }

        List<PasswordHistory> recentHistory = passwordHistoryRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId);
        for (PasswordHistory ph : recentHistory) {
            if (passwordEncoder.matches(newPassword, ph.getPasswordHash())) {
                throw new AuthException(ErrorCode.AUTH_PASSWORD_REUSED);
            }
        }

        // 4. Lưu mật khẩu mới và tăng token_version để yêu cầu các thiết bị khác làm mới
        String newHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newHash);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // 5. Lưu vào lịch sử mật khẩu
        PasswordHistory history = new PasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(newHash);
        passwordHistoryRepository.save(history);

        // 6. Xoá cache user trên instance hiện tại
        authUserCache.evict(userId);

        // 7. Gửi email thông báo và audit
        emailService.sendPasswordChangedAlert(user.getEmail(), user.getUsername(), clientIp);

        auditService.record(userId, AuditEventType.PASSWORD_CHANGED, AuditSeverity.INFO, clientIp, userAgent, Map.of(
                "action", "CHANGE_PASSWORD"
        ));
    }
}
