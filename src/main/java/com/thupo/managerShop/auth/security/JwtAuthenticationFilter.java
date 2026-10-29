package com.thupo.managerShop.auth.security;

import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Xác thực access token JWT từ header {@code Authorization: Bearer ...}.
 *
 * <p>Ngoài chữ ký và hạn dùng, token còn phải khớp trạng thái hiện tại của user (còn ACTIVE, chưa xoá mềm,
 * {@code token_version} trùng) để token của nhân viên đã nghỉ việc/bị vô hiệu bị từ chối ngay.
 * Filter không tự trả lỗi: nếu token sai chỉ ghi mã lỗi vào request attribute rồi bỏ qua; việc trả 401 do
 * {@code RestAuthenticationEntryPoint} đảm nhiệm với endpoint cần xác thực.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_ATTRIBUTE = "auth.error";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthUserCache authUserCache;

    public JwtAuthenticationFilter(JwtService jwtService, AuthUserCache authUserCache) {
        this.jwtService = jwtService;
        this.authUserCache = authUserCache;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(request, header.substring(BEARER_PREFIX.length()).trim());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            AccessTokenClaims claims = jwtService.parse(token);
            AuthUserCache.UserState state = authUserCache.get(claims.userId());
            if (!state.usable() || state.tokenVersion() != claims.tokenVersion()) {
                throw new AuthException(ErrorCode.AUTH_TOKEN_INVALID);
            }

            List<GrantedAuthority> authorities = new ArrayList<>();
            for (String role : claims.roles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                if ("ADMIN".equalsIgnoreCase(role) || "OWNER".equalsIgnoreCase(role)) {
                    authorities.add(new SimpleGrantedAuthority("MANAGE_PRODUCTS"));
                    authorities.add(new SimpleGrantedAuthority("MANAGE_INVENTORY"));
                    authorities.add(new SimpleGrantedAuthority("MANAGE_GIFT_CARDS"));
                }
            }

            AuthenticatedUser principal = new AuthenticatedUser(
                    claims.userId(),
                    claims.username(),
                    claims.roles(),
                    claims.branchId(),
                    claims.branchIds());
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        } catch (AuthException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(ERROR_ATTRIBUTE, e.getErrorCode());
        }
    }
}
