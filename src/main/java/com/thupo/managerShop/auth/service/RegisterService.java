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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service xử lý đăng ký tài khoản người dùng mới chuẩn Production.
 */
@Service
public class RegisterService {

    private static final Logger log = LoggerFactory.getLogger(RegisterService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;
    private final RateLimitStore rateLimitStore;
    private final AppSecurityProperties properties;
    private final TransactionTemplate transactionTemplate;

    public RegisterService(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           PasswordPolicyValidator passwordPolicyValidator,
                           AuditService auditService,
                           RateLimitStore rateLimitStore,
                           AppSecurityProperties properties,
                           TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
        this.rateLimitStore = rateLimitStore;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Đăng ký tài khoản người dùng mới.
     *
     * <p>Quy trình kiểm soát an toàn:
     * 1. Kiểm tra trạng thái kích hoạt tính năng đăng ký công khai.
     * 2. Rate limiting theo IP nhằm chống bot spam tài khoản và brute-force DoS CPU (BCrypt).
     * 3. Kiểm tra tính toàn vẹn của mật khẩu (xác nhận mật khẩu phải khớp).
     * 4. Kiểm tra chính sách an toàn mật khẩu (độ dài, ký tự đặc biệt, không chứa thông tin cá nhân).
     * 5. Kiểm tra trùng lặp sơ bộ (Username, Email).
     * 6. Băm mật khẩu BCrypt NGOÀI transaction (giảm thiểu thời gian giữ kết nối DB connection pool).
     * 7. Phân quyền vai trò an toàn được cấu hình (mặc định CASHIER, không tùy tiện cấp OWNER).
     * 8. Thực thi Transaction phạm vi siêu ngắn (~3-5ms) qua TransactionTemplate và phòng ngừa race condition.
     * 9. Ghi nhận Audit Log chuẩn sự kiện {@link AuditEventType#REGISTER_SUCCESS}.
     */
    public RegisterResponse register(RegisterRequest request, String clientIp, String userAgent) {
        // 1. Kiểm tra cấu hình cho phép đăng ký
        if (!properties.getRegistration().isEnabled()) {
            throw new AuthException(ErrorCode.AUTH_REGISTRATION_DISABLED,
                    "Tính năng đăng ký tài khoản tạm thời đóng, vui lòng liên hệ quản trị viên", null);
        }

        // 2. Kiểm tra xác nhận mật khẩu (rẻ nhất, chỉ so khớp RAM ~0ms)
        if (!request.isPasswordConfirmed()) {
            throw new AuthException(ErrorCode.VALIDATION_ERROR, "Mật khẩu xác nhận không khớp với mật khẩu đã nhập", null);
        }

        // 3. Chống spam đăng ký / DoS qua IP (~1ms qua Redis / In-Memory)
        checkRateLimit(clientIp);

        // 4. Chuẩn hoá dữ liệu đầu vào (Data Normalization)
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // 5. Kiểm tra chính sách mật khẩu (Password Policy)
        passwordPolicyValidator.validate(request.password(), username, email);

        // 6. Kiểm tra trùng lặp sơ bộ (~2ms DB Read)
        if (userRepository.existsByUsername(username)) {
            throw new AuthException(ErrorCode.AUTH_USER_ALREADY_EXISTS,
                    "Tên đăng nhập '" + username + "' đã tồn tại trong hệ thống", null);
        }
        if (userRepository.existsByEmail(email)) {
            throw new AuthException(ErrorCode.AUTH_USER_ALREADY_EXISTS,
                    "Email '" + email + "' đã được đăng ký trong hệ thống", null);
        }

        // 7. Băm mật khẩu bằng BCrypt (~100-150ms CPU) TRƯỚC KHI mở Transaction để giải phóng DB connection
        String passwordHash = passwordEncoder.encode(request.password());

        // 8. Xác định vai trò và trạng thái khởi tạo an toàn
        RoleName defaultRoleName = properties.getRegistration().getDefaultRole();
        if (defaultRoleName == null) {
            defaultRoleName = RoleName.CASHIER;
        }
        final RoleName finalRoleName = defaultRoleName;

        UserStatus initialStatus = properties.getRegistration().getInitialStatus();
        if (initialStatus == null) {
            initialStatus = UserStatus.ACTIVE;
        }
        final UserStatus finalInitialStatus = initialStatus;

        // 9. Thực thi ghi dữ liệu trong Transaction ngắn (~3-5ms)
        User savedUser;
        try {
            savedUser = transactionTemplate.execute(status -> {
                Role role = roleRepository.findByName(finalRoleName).orElseGet(() -> {
                    Role r = new Role();
                    r.setName(finalRoleName);
                    return roleRepository.save(r);
                });

                User user = new User();
                user.setFullName(request.fullName().trim());
                user.setUsername(username);
                user.setEmail(email);
                user.setPasswordHash(passwordHash);
                user.setStatus(finalInitialStatus);
                user.setRoles(Set.of(role));
                // Mặc định không tự tiện gán chi nhánh ngẫu nhiên; Quản trị viên/Chủ shop sẽ phân bổ sau
                user.setBranch(null);

                return userRepository.save(user);
            });
        } catch (DataIntegrityViolationException ex) {
            log.warn("Xảy ra xung đột trùng lặp (race condition) khi lưu user: username={}, email={}", username, email);
            throw new AuthException(ErrorCode.AUTH_USER_ALREADY_EXISTS,
                    "Tên đăng nhập hoặc email đã tồn tại trong hệ thống", null);
        }

        if (savedUser == null) {
            throw new AuthException(ErrorCode.INTERNAL_ERROR, "Không thể tạo tài khoản, vui lòng thử lại sau", null);
        }

        log.info("Đăng ký tài khoản thành công: id={}, username={}, email={}, role={}, status={}",
                savedUser.getId(), username, email, finalRoleName, finalInitialStatus);

        // 10. Ghi Audit Log chuẩn ngữ nghĩa bảo mật
        auditService.record(
                savedUser.getId(),
                AuditEventType.REGISTER_SUCCESS,
                AuditSeverity.INFO,
                clientIp,
                userAgent,
                Map.of("action", "REGISTER", "username", username, "email", email, "role", finalRoleName.name(), "status", finalInitialStatus.name())
        );

        String successMessage = finalInitialStatus == UserStatus.PENDING_APPROVAL
                ? "Đăng ký tài khoản thành công. Tài khoản đang chờ quản trị viên duyệt và phân bổ chi nhánh."
                : "Đăng ký tài khoản thành công. Bạn có thể đăng nhập ngay bây giờ.";

        return RegisterResponse.of(savedUser, successMessage);
    }

    private void checkRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        int limit = properties.getRateLimit().getRegisterPerMinutePerIp();
        String rateLimitKey = "register:ip:" + clientIp;
        boolean allowed = rateLimitStore.tryAcquire(rateLimitKey, limit, Duration.ofMinutes(1));
        if (!allowed) {
            log.warn("Rate limit đăng ký bị kích hoạt từ IP: {}", clientIp);
            throw new AuthException(ErrorCode.AUTH_RATE_LIMITED,
                    "Bạn đã gửi quá nhiều yêu cầu đăng ký trong thời gian ngắn. Vui lòng thử lại sau 1 phút.", 60L);
        }
    }
}
