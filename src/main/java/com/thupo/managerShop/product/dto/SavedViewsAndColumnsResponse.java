package com.thupo.managerShop.product.dto;

import java.util.List;

/**
 * Cấu hình cột và view mặc định cho trang quản lý sản phẩm.
 */
public record SavedViewsAndColumnsResponse(
        List<SavedViewItem> views,
        List<String> defaultColumns
) {
    public record SavedViewItem(
            String id,
            String name,
            String displayName,
            int position,
            List<String> columns
    ) {}
}
