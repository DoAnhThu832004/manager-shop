package com.thupo.managerShop.auth.security;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Tạo và xác minh access token JWT (HS256).
 *
 * <p>Chọn HS256 vì hiện chỉ có một ứng dụng vừa ký vừa xác minh; khi nhiều service cần xác minh độc lập
 * thì nên chuyển sang RS256. Secret lấy từ biến môi trường, không bao giờ hardcode.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final long CLOCK_SKEW_SECONDS = 5;
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_VERSION = "ver";

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(AppSecurityProperties properties) {
        AppSecurityProperties.Jwt jwt = properties.getJwt();
        byte[] secretBytes = jwt.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret (biến môi trường JWT_SECRET) phải có ít nhất 32 byte");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.issuer = jwt.getIssuer();
        this.accessTtl = Duration.ofMinutes(jwt.getAccessTtlMinutes());
    }

    /** Sinh access token. {@code user.getRoles()} phải đã được nạp (fetch join). */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Xác minh chữ ký, issuer và thời hạn.
     *
     * @throws AuthException {@code AUTH_TOKEN_EXPIRED} nếu hết hạn, {@code AUTH_TOKEN_INVALID} nếu sai/giả mạo
     */
    public AccessTokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            if (version == null || claims.getSubject() == null) {
                throw new AuthException(ErrorCode.AUTH_TOKEN_INVALID);
            }
            List<String> roles = ((List<?>) claims.get(CLAIM_ROLES, List.class)).stream()
                    .map(String::valueOf)
                    .toList();
            return new AccessTokenClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class),
                    roles,
                    version,
                    claims.getId());
        } catch (ExpiredJwtException e) {
            throw new AuthException(ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException | ClassCastException | NullPointerException e) {
            throw new AuthException(ErrorCode.AUTH_TOKEN_INVALID);
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
