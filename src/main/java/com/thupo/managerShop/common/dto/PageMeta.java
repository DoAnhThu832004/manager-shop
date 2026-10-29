package com.thupo.managerShop.common.dto;

/**
 * Metadata phân trang theo chuẩn chung: { "page", "size", "totalElements", "totalPages" }.
 */
public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
