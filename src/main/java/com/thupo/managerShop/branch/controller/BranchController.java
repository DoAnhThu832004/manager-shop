package com.thupo.managerShop.branch.controller;

import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.auth.security.IpBlockFilter;
import com.thupo.managerShop.branch.dto.CreateBranchRequest;
import com.thupo.managerShop.branch.dto.SetupBranchResponse;
import com.thupo.managerShop.branch.service.BranchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý các thao tác về Chi nhánh cửa hàng (Branch Management).
 */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    /**
     * API Khởi tạo chi nhánh gốc đầu tiên cho Chủ shop mới đăng ký.
     * Chặn quyền: Chỉ tài khoản OWNER hoặc ADMIN mới được phép khởi tạo chi nhánh.
     */
    @PostMapping("/setup-initial")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<SetupBranchResponse> setupInitialBranch(
            @Valid @RequestBody CreateBranchRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        String userAgent = servletRequest.getHeader(HttpHeaders.USER_AGENT);

        SetupBranchResponse response = branchService.setupInitialBranch(request, currentUser, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String clientIp = (String) request.getAttribute(IpBlockFilter.CLIENT_IP_ATTRIBUTE);
        return clientIp != null ? clientIp : request.getRemoteAddr();
    }
}
