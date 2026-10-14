package com.thupo.managerShop.auth.controller;

import com.thupo.managerShop.auth.dto.ChangePasswordRequest;
import com.thupo.managerShop.auth.dto.ForgotPasswordRequest;
import com.thupo.managerShop.auth.dto.LoginRequest;
import com.thupo.managerShop.auth.dto.LoginResponse;
import com.thupo.managerShop.auth.dto.MessageResponse;
import com.thupo.managerShop.auth.dto.RefreshTokenRequest;
import com.thupo.managerShop.auth.dto.ResetPasswordRequest;
import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.auth.security.IpBlockFilter;
import com.thupo.managerShop.auth.service.LoginService;
import com.thupo.managerShop.auth.service.PasswordChangeService;
import com.thupo.managerShop.auth.service.PasswordResetService;
import com.thupo.managerShop.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý các yêu cầu xác thực API prefix /api/v1/auth.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final PasswordChangeService passwordChangeService;

    public AuthController(LoginService loginService,
                          RefreshTokenService refreshTokenService,
                          PasswordResetService passwordResetService,
                          PasswordChangeService passwordChangeService) {
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.passwordChangeService = passwordChangeService;
    }

    /**
     * API Đăng nhập hệ thống bằng username hoặc email.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        LoginResponse response = loginService.login(request, clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * API Làm mới access token theo cơ chế Token Rotation và bảo vệ Reuse Detection.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                 HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        LoginResponse response = refreshTokenService.refresh(request.refreshToken(), clientIp, userAgent);
        return ResponseEntity.ok(response);
    }

    /**
     * API Đăng xuất phiên hiện tại (thu hồi refresh token gửi kèm).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser currentUser,
                                       HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        refreshTokenService.logout(request.refreshToken(), currentUser.id(), clientIp, userAgent);
        return ResponseEntity.noContent().build();
    }

    /**
     * API Đăng xuất tất cả thiết bị.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        refreshTokenService.logoutAll(currentUser.id(), clientIp, userAgent);
        return ResponseEntity.noContent().build();
    }

    /**
     * API Yêu cầu gửi email đặt lại mật khẩu.
     * Luôn trả về 200 với thông báo chung dù email có tồn tại hay không.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                          HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        passwordResetService.requestPasswordReset(request.email(), clientIp, userAgent);
        return ResponseEntity.ok(MessageResponse.of(
                "Nếu email tồn tại trong hệ thống, bạn sẽ nhận được hướng dẫn đặt lại mật khẩu trong vài phút."
        ));
    }

    /**
     * API Đặt lại mật khẩu bằng token gửi qua email.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                         HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        passwordResetService.resetPassword(request.token(), request.newPassword(), clientIp, userAgent);
        return ResponseEntity.ok(MessageResponse.of("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại với mật khẩu mới."));
    }

    /**
     * API Đổi mật khẩu cho người dùng đang đăng nhập.
     */
    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser currentUser,
                                                          HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        passwordChangeService.changePassword(currentUser.id(), request.currentPassword(), request.newPassword(), clientIp, userAgent);
        return ResponseEntity.ok(MessageResponse.of("Đổi mật khẩu thành công."));
    }

    private String extractClientIp(HttpServletRequest servletRequest) {
        String clientIp = (String) servletRequest.getAttribute(IpBlockFilter.CLIENT_IP_ATTRIBUTE);
        return clientIp != null ? clientIp : servletRequest.getRemoteAddr();
    }
}
