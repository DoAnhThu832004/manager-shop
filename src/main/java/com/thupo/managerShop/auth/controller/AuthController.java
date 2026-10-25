package com.thupo.managerShop.auth.controller;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.dto.ChangePasswordRequest;
import com.thupo.managerShop.auth.dto.ForgotPasswordRequest;
import com.thupo.managerShop.auth.dto.LoginRequest;
import com.thupo.managerShop.auth.dto.LoginResponse;
import com.thupo.managerShop.auth.dto.MessageResponse;
import com.thupo.managerShop.auth.dto.RefreshTokenRequest;
import com.thupo.managerShop.auth.dto.ResetPasswordRequest;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.auth.security.IpBlockFilter;
import com.thupo.managerShop.auth.service.LoginService;
import com.thupo.managerShop.auth.service.PasswordChangeService;
import com.thupo.managerShop.auth.service.PasswordResetService;
import com.thupo.managerShop.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý các yêu cầu xác thực API prefix /api/v1/auth.
 * Hỗ trợ Dual-Mode: Trả Token qua JSON Response cho Native POS / Mobile App
 * và Set HttpOnly Cookie an toàn cho Web Browser.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String CLIENT_TYPE_WEB = "web";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final PasswordChangeService passwordChangeService;
    private final AppSecurityProperties properties;

    public AuthController(LoginService loginService,
                          RefreshTokenService refreshTokenService,
                          PasswordResetService passwordResetService,
                          PasswordChangeService passwordChangeService,
                          AppSecurityProperties properties) {
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.passwordChangeService = passwordChangeService;
        this.properties = properties;
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
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();

        if (isWebClient(servletRequest, null)) {
            long maxAge = Duration.ofDays(properties.getJwt().getRefreshTtlDays()).toSeconds();
            builder.header(HttpHeaders.SET_COOKIE, createCookie(REFRESH_TOKEN_COOKIE, response.refreshToken(), maxAge).toString());
        }

        return builder.body(response);
    }

    /**
     * API Làm mới access token theo cơ chế Token Rotation và bảo vệ Reuse Detection.
     * Hỗ trợ nhận refreshToken từ JSON body hoặc Cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request,
                                                 @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String cookieToken,
                                                 HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        String token = resolveRefreshToken(request, cookieToken);
        LoginResponse response = refreshTokenService.refresh(token, clientIp, userAgent);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();

        if (isWebClient(servletRequest, cookieToken)) {
            long maxAge = Duration.ofDays(properties.getJwt().getRefreshTtlDays()).toSeconds();
            builder.header(HttpHeaders.SET_COOKIE, createCookie(REFRESH_TOKEN_COOKIE, response.refreshToken(), maxAge).toString());
        }

        return builder.body(response);
    }

    /**
     * API Đăng xuất phiên hiện tại (thu hồi refresh token gửi kèm).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request,
                                       @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String cookieToken,
                                       @AuthenticationPrincipal AuthenticatedUser currentUser,
                                       HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        String token = resolveRefreshToken(request, cookieToken);
        refreshTokenService.logout(token, currentUser.id(), clientIp, userAgent);
        ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.noContent();

        if (isWebClient(servletRequest, cookieToken)) {
            builder.header(HttpHeaders.SET_COOKIE, createCookie(REFRESH_TOKEN_COOKIE, "", 0).toString());
        }

        return builder.build();
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
        ResponseEntity.HeadersBuilder<?> builder = ResponseEntity.noContent();

        if (isWebClient(servletRequest, null)) {
            builder.header(HttpHeaders.SET_COOKIE, createCookie(REFRESH_TOKEN_COOKIE, "", 0).toString());
        }

        return builder.build();
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

    private String resolveRefreshToken(RefreshTokenRequest request, String cookieToken) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken().trim();
        }
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken.trim();
        }
        throw new AuthException(ErrorCode.VALIDATION_ERROR, "Refresh token không được để trống", null);
    }

    private boolean isWebClient(HttpServletRequest servletRequest, String cookieToken) {
        String clientType = servletRequest.getHeader(CLIENT_TYPE_HEADER);
        return CLIENT_TYPE_WEB.equalsIgnoreCase(clientType) || (cookieToken != null && !cookieToken.isBlank());
    }

    private ResponseCookie createCookie(String name, String value, long maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false) // Bật true trên production có HTTPS
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    private String extractClientIp(HttpServletRequest servletRequest) {
        String clientIp = (String) servletRequest.getAttribute(IpBlockFilter.CLIENT_IP_ATTRIBUTE);
        return clientIp != null ? clientIp : servletRequest.getRemoteAddr();
    }
}
