package com.thupo.managerShop.auth.security;

import java.util.List;

/**
 * Nội dung đã được xác minh chữ ký của một access token.
 * Bao gồm thông tin user, vai trò và chi nhánh (hỗ trợ phân quyền dữ liệu đa chi nhánh).
 */
public record AccessTokenClaims(
        Long userId,
        String username,
        List<String> roles,
        Long branchId,
        List<Long> branchIds,
        int tokenVersion,
        String jti
) {
    /** Constructor tương thích ngược cho các unit test cũ. */
    public AccessTokenClaims(Long userId, String username, List<String> roles, int tokenVersion, String jti) {
        this(userId, username, roles, null, List.of(), tokenVersion, jti);
    }
}
