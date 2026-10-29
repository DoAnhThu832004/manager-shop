package com.thupo.managerShop.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cấu trúc Response chuẩn cho toàn bộ API nghiệp vụ.
 * Thành công: { "data": ... }
 * Danh sách: { "data": [...], "meta": { "page": 0, "size": 20, "totalElements": 100, "totalPages": 5 } }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        T data,
        PageMeta meta
) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, PageMeta meta) {
        return new ApiResponse<>(data, meta);
    }
}
