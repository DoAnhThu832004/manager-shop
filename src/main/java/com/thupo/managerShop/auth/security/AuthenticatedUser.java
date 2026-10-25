package com.thupo.managerShop.auth.security;

import java.util.List;

/**
 * Principal đặt vào SecurityContext sau khi xác thực JWT thành công.
 * Hỗ trợ phân quyền cấp dữ liệu đa chi nhánh.
 */
public record AuthenticatedUser(
        Long id,
        String username,
        List<String> roles,
        Long branchId,
        List<Long> branchIds
) {
    /** Constructor tương thích ngược cho các controller/test cũ. */
    public AuthenticatedUser(Long id, String username, List<String> roles) {
        this(id, username, roles, null, List.of());
    }
}
