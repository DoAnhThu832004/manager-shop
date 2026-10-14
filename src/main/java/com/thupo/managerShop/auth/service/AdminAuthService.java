package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.dto.LoginAttemptResponse;
import com.thupo.managerShop.auth.entity.LoginAttempt;
import com.thupo.managerShop.auth.repository.LoginAttemptRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service phục vụ các tính năng quản trị bảo mật của Admin (OWNER / MANAGER).
 */
@Service
public class AdminAuthService {

    private final AccountLockService accountLockService;
    private final IpBlockService ipBlockService;
    private final LoginAttemptRepository loginAttemptRepository;

    public AdminAuthService(AccountLockService accountLockService,
                            IpBlockService ipBlockService,
                            LoginAttemptRepository loginAttemptRepository) {
        this.accountLockService = accountLockService;
        this.ipBlockService = ipBlockService;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    /**
     * Mở khoá tài khoản bị khoá do brute-force.
     */
    public void unlockUser(Long userId, String adminUsername, String ip, String userAgent) {
        accountLockService.unlockUser(userId, adminUsername, ip, userAgent);
    }

    /**
     * Gỡ bỏ chặn IP.
     */
    public void unblockIp(String ipToUnblock, String adminUsername, String userAgent) {
        ipBlockService.unblockIp(ipToUnblock, adminUsername, userAgent);
    }

    /**
     * Tra cứu lịch sử đăng nhập có phân trang và bộ lọc linh hoạt.
     */
    @Transactional(readOnly = true)
    public Page<LoginAttemptResponse> getLoginAttempts(String ip, String username, Boolean success,
                                                       Instant from, Instant to, Pageable pageable) {
        Specification<LoginAttempt> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (ip != null && !ip.isBlank()) {
                predicates.add(cb.equal(root.get("ipAddress"), ip.trim()));
            }
            if (username != null && !username.isBlank()) {
                predicates.add(cb.equal(root.get("usernameAttempted"), username.trim().toLowerCase()));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return loginAttemptRepository.findAll(spec, pageable)
                .map(item -> new LoginAttemptResponse(
                        item.getId(),
                        item.getUsernameAttempted(),
                        item.getIpAddress(),
                        item.isSuccess(),
                        item.getFailureReason(),
                        item.getUserAgent(),
                        item.getCreatedAt()
                ));
    }
}
