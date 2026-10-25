package com.thupo.managerShop.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Helper hỗ trợ kiểm tra quyền truy cập chi nhánh trong Spring Security SpEL:
 * Ví dụ: {@code @PreAuthorize("@branchSecurity.canAccess(#branchId)")}
 */
@Component("branchSecurity")
public class BranchSecurityExpression {

    /**
     * Kiểm tra user hiện tại có quyền thực hiện thao tác trên chi nhánh chỉ định không.
     * - OWNER: Toàn quyền truy cập mọi chi nhánh.
     * - Các vai trò khác: Phải có branchId trùng với chi nhánh trực thuộc hoặc nằm trong danh sách uỷ quyền.
     */
    public boolean canAccess(Long targetBranchId) {
        if (targetBranchId == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }

        if (user.roles() != null && user.roles().contains("OWNER")) {
            return true;
        }

        if (targetBranchId.equals(user.branchId())) {
            return true;
        }

        return user.branchIds() != null && user.branchIds().contains(targetBranchId);
    }
}
