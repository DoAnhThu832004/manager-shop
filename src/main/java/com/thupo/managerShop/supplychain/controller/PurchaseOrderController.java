package com.thupo.managerShop.supplychain.controller;

import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.security.RequirePermission;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderCreateRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiveRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderResponse;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderUpdateRequest;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderStatus;
import com.thupo.managerShop.supplychain.service.PurchaseOrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho Module Đơn đặt hàng nhập (Purchase Orders).
 */
@RestController
@RequestMapping("/api/v1/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getPurchaseOrders(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long destinationLocationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiResponse<List<PurchaseOrderResponse>> response = purchaseOrderService.getPurchaseOrders(
                status, supplierId, destinationLocationId, from, to, q, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Alias endpoint theo hợp đồng OpenAPI / HAR: /api/v1/purchase-orders/inventory-purchase-orders-list
     */
    @GetMapping("/inventory-purchase-orders-list")
    public ResponseEntity<ApiResponse<List<PurchaseOrderResponse>>> getPurchaseOrdersAlias(
            @RequestParam(required = false) PurchaseOrderStatus status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long destinationLocationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getPurchaseOrders(status, supplierId, destinationLocationId, from, to, q, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> getPurchaseOrder(@PathVariable Long id) {
        PurchaseOrderResponse response = purchaseOrderService.getPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> createPurchaseOrder(
            @Valid @RequestBody PurchaseOrderCreateRequest request) {
        PurchaseOrderResponse created = purchaseOrderService.createPurchaseOrder(request);
        return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + created.id()))
                .body(ApiResponse.of(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> updatePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderUpdateRequest request) {
        PurchaseOrderResponse updated = purchaseOrderService.updatePurchaseOrder(id, request);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    @PostMapping("/{id}/order")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> orderPurchaseOrder(@PathVariable Long id) {
        PurchaseOrderResponse ordered = purchaseOrderService.orderPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.of(ordered));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> receivePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderReceiveRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = (user != null) ? user.id() : null;
        PurchaseOrderResponse received = purchaseOrderService.receivePurchaseOrder(id, request, userId);
        return ResponseEntity.ok(ApiResponse.of(received));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> closePurchaseOrder(@PathVariable Long id) {
        PurchaseOrderResponse closed = purchaseOrderService.closePurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.of(closed));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<PurchaseOrderResponse>> cancelPurchaseOrder(@PathVariable Long id) {
        PurchaseOrderResponse cancelled = purchaseOrderService.cancelPurchaseOrder(id);
        return ResponseEntity.ok(ApiResponse.of(cancelled));
    }
}
