package com.thupo.managerShop.auth.controller;

import com.thupo.managerShop.auth.dto.LoginAttemptResponse;
import com.thupo.managerShop.auth.dto.MessageResponse;
import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.auth.security.IpBlockFilter;
import com.thupo.managerShop.auth.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller API quản trị bảo mật dành riêng cho OWNER và MANAGER.
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /**
     * Mở khoá tài khoản bị khoá tạm thời do brute-force.
     */
    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<MessageResponse> unlockUser(
            @PathVariable("id") Long userId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        adminAuthService.unlockUser(userId, currentUser.username(), clientIp, userAgent);
        return ResponseEntity.ok(MessageResponse.of("Đã mở khoá tài khoản thành công"));
    }

    /**
     * Gỡ bỏ chặn IP.
     */
    @DeleteMapping("/ip-blocks/{ip}")
    public ResponseEntity<Void> unblockIp(
            @PathVariable("ip") String ip,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        adminAuthService.unblockIp(ip, currentUser.username(), userAgent);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tra cứu lịch sử đăng nhập có phân trang và bộ lọc.
     */
    @GetMapping("/login-attempts")
    public ResponseEntity<Page<LoginAttemptResponse>> getLoginAttempts(
            @RequestParam(name = "ip", required = false) String ip,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "success", required = false) Boolean success,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<LoginAttemptResponse> results = adminAuthService.getLoginAttempts(ip, username, success, from, to, pageable);
        return ResponseEntity.ok(results);
    }

    private String extractClientIp(HttpServletRequest request) {
        String clientIp = (String) request.getAttribute(IpBlockFilter.CLIENT_IP_ATTRIBUTE);
        return clientIp != null ? clientIp : request.getRemoteAddr();
    }
}
