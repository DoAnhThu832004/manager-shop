package com.thupo.managerShop.supplychain.controller;

import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.security.RequirePermission;
import com.thupo.managerShop.supplychain.dto.InventoryAdjustmentRequest;
import com.thupo.managerShop.supplychain.dto.InventoryLevelResponse;
import com.thupo.managerShop.supplychain.dto.InventoryMovementResponse;
import com.thupo.managerShop.supplychain.service.InventoryService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho Module Tồn kho đa điểm.
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Danh sách tồn kho theo biến thể và địa điểm kho.
     * Cho phép mọi người dùng đã đăng nhập truy cập GET.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryLevelResponse>>> getInventory(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer lowStockThreshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiResponse<List<InventoryLevelResponse>> response = inventoryService.getInventory(
                locationId, variantId, productId, q, lowStockThreshold, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Alias endpoint theo hợp đồng HAR / openapi.yaml: /api/v1/inventory/inventory-resource-index-items.
     */
    @GetMapping("/inventory-resource-index-items")
    public ResponseEntity<ApiResponse<List<InventoryLevelResponse>>> getInventoryAlias(
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer lowStockThreshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getInventory(locationId, variantId, productId, q, lowStockThreshold, page, size);
    }

    /**
     * Lịch sử biến động sổ cái kho (Movements).
     */
    @GetMapping("/movements")
    public ResponseEntity<ApiResponse<List<InventoryMovementResponse>>> getMovements(
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiResponse<List<InventoryMovementResponse>> response = inventoryService.getMovements(
                variantId, locationId, from, to, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Điều chỉnh tồn kho theo mô hình kiểm đếm hoặc theo delta (Batch).
     * Quyền: Cần quyền MANAGE_INVENTORY hoặc vai trò ADMIN/OWNER. STAFF bị chặn 403.
     */
    @PostMapping("/adjustments")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<List<InventoryLevelResponse>>> adjustInventory(
            @Valid @RequestBody InventoryAdjustmentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = (user != null) ? user.id() : null;
        List<InventoryLevelResponse> results = inventoryService.adjustBatch(request, userId);
        return ResponseEntity.ok(ApiResponse.of(results));
    }
}
