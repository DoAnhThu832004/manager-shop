package com.thupo.managerShop.branch.service;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.RefreshToken;
import com.thupo.managerShop.auth.entity.RefreshTokenStatus;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.exception.ErrorCode;
import com.thupo.managerShop.auth.repository.BranchRepository;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.auth.security.JwtService;
import com.thupo.managerShop.auth.security.TokenHashUtil;
import com.thupo.managerShop.branch.dto.CreateBranchRequest;
import com.thupo.managerShop.branch.dto.SetupBranchResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý khởi tạo và quản lý chi nhánh cửa hàng.
 */
@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AppSecurityProperties properties;

    public BranchService(BranchRepository branchRepository,
                         UserRepository userRepository,
                         RefreshTokenRepository refreshTokenRepository,
                         JwtService jwtService,
                         AppSecurityProperties properties) {
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    /**
     * Khởi tạo chi nhánh đầu tiên cho Chủ shop (OWNER/ADMIN) và cấp lại cặp Token mới
     * có nhúng claim 'bid' (branchId) để đồng bộ hoàn toàn với SecurityContext.
     */
    @Transactional
    public SetupBranchResponse setupInitialBranch(CreateBranchRequest request,
                                                  AuthenticatedUser currentUser,
                                                  String clientIp,
                                                  String userAgent) {
        User user = userRepository.findByIdWithRoles(currentUser.id())
                .orElseThrow(() -> new AuthException(ErrorCode.NOT_FOUND, "Không tìm thấy thông tin người dùng"));

        // Chốt chặn 1: Idempotency (Ngăn tạo lại nếu tài khoản đã có chi nhánh)
        if (user.getBranch() != null) {
            throw new AuthException(ErrorCode.USER_ALREADY_HAS_BRANCH);
        }

        // Chốt chặn 2: Kiểm tra trùng mã chi nhánh trước khi persist
        String cleanCode = request.code().trim().toUpperCase();
        if (branchRepository.existsByCodeIgnoreCase(cleanCode)) {
            throw new AuthException(ErrorCode.BRANCH_ALREADY_EXISTS);
        }

        // Tạo chi nhánh mới
        Branch branch = new Branch();
        branch.setCode(cleanCode);
        branch.setName(request.name().trim());
        branch.setAddress(request.address() != null ? request.address().trim() : null);
        branch.setPhone(request.phone() != null ? request.phone().trim() : null);
        branch.setStatus("ACTIVE");
        Branch savedBranch = branchRepository.save(branch);

        // Gán chi nhánh làm chi nhánh chính và chi nhánh được phân quyền của User
        user.setBranch(savedBranch);
        user.getAccessibleBranches().add(savedBranch);
        userRepository.save(user);

        // Chốt chặn 3: Cấp lại AccessToken (chứa claim 'bid') và RefreshToken mới
        List<Long> branchIds = userRepository.findAccessibleBranchIdsByUserId(user.getId());
        String newAccessToken = jwtService.generateAccessToken(user, branchIds);

        String rawRefreshToken = TokenHashUtil.generateRandomToken(32);
        String refreshTokenHash = TokenHashUtil.sha256Hex(rawRefreshToken);
        Instant now = Instant.now();
        Instant refreshExpiresAt = now.plus(Duration.ofDays(properties.getJwt().getRefreshTtlDays()));

        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setUserId(user.getId());
        refreshTokenEntity.setTokenHash(refreshTokenHash);
        refreshTokenEntity.setFamilyId(UUID.randomUUID().toString());
        refreshTokenEntity.setStatus(RefreshTokenStatus.ACTIVE);
        refreshTokenEntity.setExpiresAt(refreshExpiresAt);
        refreshTokenEntity.setIpAddress(clientIp);
        refreshTokenEntity.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        refreshTokenRepository.save(refreshTokenEntity);

        return SetupBranchResponse.of(
                savedBranch.getId(),
                savedBranch.getCode(),
                savedBranch.getName(),
                savedBranch.getAddress(),
                savedBranch.getPhone(),
                newAccessToken,
                rawRefreshToken,
                jwtService.getAccessTtlSeconds()
        );
    }
}
