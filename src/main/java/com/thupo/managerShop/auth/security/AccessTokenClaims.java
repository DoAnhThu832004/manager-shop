package com.thupo.managerShop.auth.security;

import java.util.List;

/** Nội dung đã được xác minh chữ ký của một access token. */
public record AccessTokenClaims(Long userId, String username, List<String> roles, int tokenVersion, String jti) {
}
