package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
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
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.AuthUserCache;
import com.thupo.managerShop.auth.security.JwtService;
import com.thupo.managerShop.auth.security.TokenHashUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý vòng đời Refresh Token:
 * - Refresh Token Rotation (mỗi lần refresh sinh token mới, token cũ thành USED)
 * - Reuse Detection: phát hiện tái sử dụng token cũ -> thu hồi cả họ token (Token Family)
 * - Grace Period: hỗ trợ client gửi 2 request refresh đồng thời (do network retry/đa luồng) mà không bị phạt oan
 * - Đăng xuất đơn lẻ và đăng xuất tất cả thiết bị
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthUserCache authUserCache;
    private final AuditService auditService;
    private final AppSecurityProperties properties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtService jwtService,
                               AuthUserCache authUserCache,
                               AuditService auditService,
                               AppSecurityProperties properties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authUserCache = authUserCache;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * Làm mới token theo cơ chế xoay vòng (Rotation) và bảo vệ Reuse Detection.
     * Sử dụng Atomic Conditional Update để cập nhật trạng thái ngay lập tức,
     * loại bỏ độ trễ và tranh chấp khoá của SELECT ... FOR UPDATE.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public LoginResponse refresh(String rawRefreshToken, String clientIp, String userAgent) {
        String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        Instant now = Instant.now();

        // 1. Thử cập nhật nguyên tử từ ACTIVE -> USED (Atomic CAS)
        int updated = refreshTokenRepository.markTokenAsUsedAtomic(tokenHash, now);

        RefreshToken token = (updated == 1
                ? refreshTokenRepository.findByTokenHash(tokenHash)
                : refreshTokenRepository.findByTokenHashForUpdate(tokenHash))
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_TOKEN_INVALID));

        User user = userRepository.findByIdWithRoles(token.getUserId())
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_TOKEN_INVALID));

        // Kiểm tra trạng thái tài khoản
        if (user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
            throw new AuthException(ErrorCode.AUTH_ACCOUNT_DISABLED);
        }

        // Kiểm tra hết hạn
        if (token.getExpiresAt().isBefore(now)) {
            throw new AuthException(ErrorCode.AUTH_TOKEN_EXPIRED);
        }

        // Token đã bị thu hồi
        if (token.getStatus() == RefreshTokenStatus.REVOKED) {
            throw new AuthException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        // Phát hiện token đã qua sử dụng (USED) ngoài lượt cập nhật hiện tại
        if (token.getStatus() == RefreshTokenStatus.USED && updated == 0) {
            handleUsedToken(token, user, clientIp, userAgent, now);
        }

        if (updated == 0) {
            token.setStatus(RefreshTokenStatus.USED);
            token.setUsedAt(now);
            refreshTokenRepository.save(token);
        }

        // Sinh Refresh Token mới kế tiếp trong cùng một Family
        String newRawRefreshToken = TokenHashUtil.generateRandomToken(32);
        String newTokenHash = TokenHashUtil.sha256Hex(newRawRefreshToken);

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setTokenHash(newTokenHash);
        newRefreshToken.setFamilyId(token.getFamilyId()); // Cùng token family
        newRefreshToken.setStatus(RefreshTokenStatus.ACTIVE);
        newRefreshToken.setExpiresAt(now.plus(Duration.ofDays(properties.getJwt().getRefreshTtlDays())));
        newRefreshToken.setDeviceInfo(token.getDeviceInfo());
        newRefreshToken.setIpAddress(clientIp);
        newRefreshToken.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);

        refreshTokenRepository.save(newRefreshToken);

        List<Long> branchIds = userRepository.findAccessibleBranchIdsByUserId(user.getId());
        String newAccessToken = jwtService.generateAccessToken(user, branchIds);
        List<String> roleNames = user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList();

        Long branchId = user.getBranch() != null ? user.getBranch().getId() : null;
        String branchName = user.getBranch() != null ? user.getBranch().getName() : null;

        return LoginResponse.of(
                newAccessToken,
                newRawRefreshToken,
                jwtService.getAccessTtlSeconds(),
                roleNames,
                branchId,
                branchName,
                false
        );
    }

    private void handleUsedToken(RefreshToken token, User user, String clientIp, String userAgent, Instant now) {
        int graceSeconds = properties.getJwt().getRefreshGraceSeconds();
        Instant usedAt = token.getUsedAt();

        // Nếu request đến trong khoảng Grace Period (vài giây đầu sau khi xoay vòng)
        // Cho phép xem là race condition từ client, nhưng nếu ngoài Grace Period -> Phát hiện bị đánh cắp
        boolean inGracePeriod = usedAt != null && Duration.between(usedAt, now).toSeconds() <= graceSeconds;

        if (inGracePeriod) {
            log.warn("Nhận request refresh token USED trong khoảng grace period ({}s) cho user [{}]", graceSeconds, user.getId());
            // Trong grace period, tiếp tục cho phép luồng tiếp diễn bằng cách tạo thêm token nối tiếp
            return;
        }

        // Vượt quá Grace Period: TÁI SỬ DỤNG TOKEN ĐÃ DÙNG (TOKEN REUSE ATTACK)
        // Thu hồi toàn bộ Token Family của phiên này để bảo vệ tài khoản
        refreshTokenRepository.revokeFamily(token.getFamilyId());

        auditService.record(user.getId(), AuditEventType.REFRESH_TOKEN_REUSE_DETECTED, AuditSeverity.HIGH, clientIp, userAgent, Map.of(
                "familyId", token.getFamilyId(),
                "warning", "Phát hiện tái sử dụng refresh token đã hết hiệu lực, thu hồi cả họ token!"
        ));

        log.error("CẢNH BÁO: Phát hiện tái sử dụng refresh token của user [{}]! Đã thu hồi toàn bộ token family [{}]",
                user.getId(), token.getFamilyId());

        throw new AuthException(ErrorCode.AUTH_REFRESH_REUSED);
    }

    /**
     * Đăng xuất phiên hiện tại: thu hồi toàn bộ token family liên kết với refresh token này.
     */
    @Transactional
    public void logout(String rawRefreshToken, Long userId, String ip, String userAgent) {
        String tokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getUserId().equals(userId)) {
                refreshTokenRepository.revokeFamily(token.getFamilyId());
                auditService.record(userId, AuditEventType.SESSION_REVOKED, AuditSeverity.INFO, ip, userAgent, Map.of(
                        "familyId", token.getFamilyId(),
                        "action", "LOGOUT"
                ));
            }
        });
    }

    /**
     * Đăng xuất tất cả thiết bị:
     * 1. Thu hồi mọi refresh token của user.
     * 2. Tăng token_version trong bảng users để vô hiệu hoá tức thì mọi access token đang còn hạn.
     * 3. Xoá cache người dùng trong AuthUserCache.
     */
    @Transactional
    public void logoutAll(Long userId, String ip, String userAgent) {
        refreshTokenRepository.revokeAllByUserId(userId);
        userRepository.bumpTokenVersion(userId);
        authUserCache.evict(userId);

        auditService.record(userId, AuditEventType.LOGOUT_ALL, AuditSeverity.INFO, ip, userAgent, Map.of(
                "action", "LOGOUT_ALL_DEVICES"
        ));
        log.info("Người dùng [userId={}] đã đăng xuất toàn bộ thiết bị", userId);
    }
}
