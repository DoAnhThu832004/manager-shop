package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.dto.RegisterRequest;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.RoleName;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.repository.BranchRepository;
import com.thupo.managerShop.auth.repository.RoleRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý đăng ký tài khoản mới cho cửa hàng thời trang.
 */
@Service
public class RegisterService {

    private static final Logger log = LoggerFactory.getLogger(RegisterService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;

    public RegisterService(UserRepository userRepository,
                           RoleRepository roleRepository,
                           BranchRepository branchRepository,
                           PasswordEncoder passwordEncoder,
                           PasswordPolicyValidator passwordPolicyValidator,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.branchRepository = branchRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
    }

    @Transactional
    public User register(RegisterRequest request, String clientIp, String userAgent) {
        String rawIdentifier = request.identifier().trim();
        String username;
        String email;

        if (rawIdentifier.contains("@")) {
            email = rawIdentifier.toLowerCase(Locale.ROOT);
            String prefix = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "");
            if (prefix.length() < 3) {
                prefix = "shop_" + prefix;
            }
            username = prefix;
            if (userRepository.existsByUsername(username)) {
                username = prefix + "_" + (System.currentTimeMillis() % 10000);
            }
        } else {
            username = rawIdentifier.toLowerCase(Locale.ROOT).replaceAll("[^a-zA-Z0-9_]", "");
            if (username.length() < 3) {
                username = "shop_" + username;
            }
            email = username + "@managershop.app";
        }

        // Kiểm tra trùng lặp email hoặc username
        if (userRepository.existsByEmail(email)) {
            throw new AuthException(ErrorCode.VALIDATION_ERROR, "Email " + email + " đã được đăng ký trong hệ thống", null);
        }
        if (userRepository.existsByUsername(username)) {
            throw new AuthException(ErrorCode.VALIDATION_ERROR, "Tên đăng nhập " + username + " đã tồn tại trong hệ thống", null);
        }

        // Kiểm tra chính sách an toàn mật khẩu chuẩn backend
        passwordPolicyValidator.validate(request.password(), username, email);

        // Khởi tạo thực thể User
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);

        // Gán vai trò OWNER cho người đăng ký mở shop
        Role ownerRole = roleRepository.findByName(RoleName.OWNER).orElseGet(() -> {
            Role r = new Role();
            r.setName(RoleName.OWNER);
            return roleRepository.save(r);
        });
        user.setRoles(Set.of(ownerRole));

        // Gán chi nhánh mặc định (nếu có)
        branchRepository.findAll().stream().findFirst().ifPresent(user::setBranch);

        User savedUser = userRepository.save(user);

        log.info("Đăng ký tài khoản thành công: id={}, username={}, email={}", savedUser.getId(), username, email);

        auditService.record(savedUser.getId(), AuditEventType.LOGIN_SUCCESS, AuditSeverity.INFO,
                clientIp, userAgent, Map.of("action", "REGISTER", "username", username));

        return savedUser;
    }
}
