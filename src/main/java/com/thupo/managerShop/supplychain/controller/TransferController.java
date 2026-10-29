package com.thupo.managerShop.supplychain.controller;

import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.security.RequirePermission;
import com.thupo.managerShop.supplychain.dto.InventoryTransferCreateRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferReceiveRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferResponse;
import com.thupo.managerShop.supplychain.dto.InventoryTransferUpdateRequest;
import com.thupo.managerShop.supplychain.entity.TransferStatus;
import com.thupo.managerShop.supplychain.service.InventoryTransferService;
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
 * REST Controller cho Module Chuyển kho nội bộ (Inventory Transfers).
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final InventoryTransferService inventoryTransferService;

    public TransferController(InventoryTransferService inventoryTransferService) {
        this.inventoryTransferService = inventoryTransferService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryTransferResponse>>> getTransfers(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Long originLocationId,
            @RequestParam(required = false) Long destinationLocationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiResponse<List<InventoryTransferResponse>> response = inventoryTransferService.getTransfers(
                status, originLocationId, destinationLocationId, from, to, q, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Alias endpoint theo hợp đồng OpenAPI / HAR: /api/v1/transfers/unified-transfers-list
     */
    @GetMapping("/unified-transfers-list")
    public ResponseEntity<ApiResponse<List<InventoryTransferResponse>>> getTransfersAlias(
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) Long originLocationId,
            @RequestParam(required = false) Long destinationLocationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getTransfers(status, originLocationId, destinationLocationId, from, to, q, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InventoryTransferResponse>> getTransfer(@PathVariable Long id) {
        InventoryTransferResponse response = inventoryTransferService.getTransfer(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<InventoryTransferResponse>> createTransfer(
            @Valid @RequestBody InventoryTransferCreateRequest request) {
        InventoryTransferResponse created = inventoryTransferService.createTransfer(request);
        return ResponseEntity.created(URI.create("/api/v1/transfers/" + created.id()))
                .body(ApiResponse.of(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<InventoryTransferResponse>> updateTransfer(
            @PathVariable Long id,
            @Valid @RequestBody InventoryTransferUpdateRequest request) {
        InventoryTransferResponse updated = inventoryTransferService.updateTransfer(id, request);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<InventoryTransferResponse>> sendTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = (user != null) ? user.id() : null;
        InventoryTransferResponse sent = inventoryTransferService.sendTransfer(id, userId);
        return ResponseEntity.ok(ApiResponse.of(sent));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<InventoryTransferResponse>> receiveTransfer(
            @PathVariable Long id,
            @Valid @RequestBody InventoryTransferReceiveRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = (user != null) ? user.id() : null;
        InventoryTransferResponse received = inventoryTransferService.receiveTransfer(id, request, userId);
        return ResponseEntity.ok(ApiResponse.of(received));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MANAGE_INVENTORY') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_INVENTORY")
    public ResponseEntity<ApiResponse<InventoryTransferResponse>> cancelTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        Long userId = (user != null) ? user.id() : null;
        InventoryTransferResponse cancelled = inventoryTransferService.cancelTransfer(id, userId);
        return ResponseEntity.ok(ApiResponse.of(cancelled));
    }
}
