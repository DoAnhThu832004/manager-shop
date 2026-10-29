package com.thupo.managerShop.branch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO phản hồi sau khi khởi tạo chi nhánh thành công.
 * Trả về thông tin chi nhánh mới KÈM THEO cặp Token mới đã nhúng claim 'bid' (branchId)
 * để đồng bộ toàn diện với SecurityContext của hệ thống.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetupBranchResponse(
        Long branchId,
        String branchCode,
        String branchName,
        String address,
        String phone,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static SetupBranchResponse of(Long branchId, String branchCode, String branchName,
                                        String address, String phone,
                                        String accessToken, String refreshToken, long expiresIn) {
        return new SetupBranchResponse(branchId, branchCode, branchName, address, phone,
                accessToken, refreshToken, "Bearer", expiresIn);
    }
}
