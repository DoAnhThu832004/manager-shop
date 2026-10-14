package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.dto.SessionResponse;
import com.thupo.managerShop.auth.entity.AuditEventType;
import com.thupo.managerShop.auth.entity.AuditSeverity;
import com.thupo.managerShop.auth.entity.RefreshToken;
import com.thupo.managerShop.auth.entity.RefreshTokenStatus;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý danh sách phiên đăng nhập của người dùng.
 */
@Service
public class SessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    public SessionService(RefreshTokenRepository refreshTokenRepository, AuditService auditService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    /**
     * Lấy danh sách các phiên đăng nhập đang hoạt động của người dùng.
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(Long userId) {
        Instant now = Instant.now();
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(userId, RefreshTokenStatus.ACTIVE, now);

        return activeTokens.stream()
                .map(token -> new SessionResponse(
                        token.getId(),
                        token.getDeviceInfo() != null ? token.getDeviceInfo() : "Thiết bị không rõ",
                        token.getIpAddress(),
                        token.getUserAgent(),
                        token.getCreatedAt(),
                        token.getExpiresAt(),
                        false // Được client đối chiếu theo token của phiên hiện tại nếu cần
                ))
                .toList();
    }

    /**
     * Thu hồi một phiên đăng nhập cụ thể của người dùng.
     */
    @Transactional
    public void revokeSession(Long userId, Long sessionId, String ip, String userAgent) {
        RefreshToken token = refreshTokenRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AuthException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên đăng nhập yêu cầu", null));

        // Thu hồi toàn bộ family của phiên đó
        refreshTokenRepository.revokeFamily(token.getFamilyId());

        auditService.record(userId, AuditEventType.SESSION_REVOKED, AuditSeverity.INFO, ip, userAgent, Map.of(
                "sessionId", sessionId,
                "familyId", token.getFamilyId()
        ));
    }
}
