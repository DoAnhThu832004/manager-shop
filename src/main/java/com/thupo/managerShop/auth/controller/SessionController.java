package com.thupo.managerShop.auth.controller;

import com.thupo.managerShop.auth.dto.SessionResponse;
import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.auth.security.IpBlockFilter;
import com.thupo.managerShop.auth.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý các phiên đăng nhập của người dùng hiện tại.
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Lấy danh sách các phiên đăng nhập đang hoạt động của tài khoản hiện tại.
     */
    @GetMapping
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<SessionResponse> sessions = sessionService.getActiveSessions(currentUser.id());
        return ResponseEntity.ok(sessions);
    }

    /**
     * Thu hồi (đăng xuất) một phiên đăng nhập cụ thể theo ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable("id") Long sessionId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        String clientIp = (String) request.getAttribute(IpBlockFilter.CLIENT_IP_ATTRIBUTE);
        if (clientIp == null) {
            clientIp = request.getRemoteAddr();
        }
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        sessionService.revokeSession(currentUser.id(), sessionId, clientIp, userAgent);
        return ResponseEntity.noContent().build();
    }
}
