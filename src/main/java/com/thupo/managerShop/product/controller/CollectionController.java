package com.thupo.managerShop.product.controller;

import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.security.RequirePermission;
import com.thupo.managerShop.product.dto.AddProductToCollectionRequest;
import com.thupo.managerShop.product.dto.CollectionCreateRequest;
import com.thupo.managerShop.product.dto.CollectionListResponse;
import com.thupo.managerShop.product.dto.CollectionResponse;
import com.thupo.managerShop.product.dto.CollectionUpdateRequest;
import com.thupo.managerShop.product.entity.CollectionType;
import com.thupo.managerShop.product.service.CollectionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho Module Bộ sưu tập (Collection).
 */
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /**
     * Danh sách bộ sưu tập có tìm kiếm, lọc theo loại và phân trang.
     * Cho phép mọi người dùng đã đăng nhập (Staff, Admin, Owner...) truy cập GET.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CollectionListResponse>>> getCollections(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CollectionType collectionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ApiResponse<List<CollectionListResponse>> response = collectionService.getCollections(q, collectionType, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Alias endpoint theo hợp đồng HAR / openapi.yaml: /api/v1/collections/collection-nodes-0.
     */
    @GetMapping("/collection-nodes-0")
    public ResponseEntity<ApiResponse<List<CollectionListResponse>>> getCollectionsAlias(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CollectionType collectionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return getCollections(q, collectionType, page, size);
    }

    /**
     * Chi tiết bộ sưu tập kèm danh sách sản phẩm phân trang.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CollectionResponse>> getCollection(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int productPage,
            @RequestParam(defaultValue = "20") int productSize) {
        CollectionResponse response = collectionService.getCollection(id, productPage, productSize);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * Tạo mới bộ sưu tập (MANUAL hoặc AUTOMATED).
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER. STAFF bị chặn 403.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<ApiResponse<CollectionResponse>> createCollection(
            @Valid @RequestBody CollectionCreateRequest request) {
        CollectionResponse response = collectionService.createCollection(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    /**
     * Cập nhật thông tin bộ sưu tập.
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER. STAFF bị chặn 403.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<ApiResponse<CollectionResponse>> updateCollection(
            @PathVariable Long id,
            @Valid @RequestBody CollectionUpdateRequest request) {
        CollectionResponse response = collectionService.updateCollection(id, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * Xóa mềm bộ sưu tập.
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER. STAFF bị chặn 403.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<Void> deleteCollection(@PathVariable Long id) {
        collectionService.deleteCollection(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Thêm sản phẩm vào bộ sưu tập thủ công (MANUAL).
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER.
     */
    @PostMapping("/{id}/products")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<Void> addProductToCollection(
            @PathVariable Long id,
            @RequestBody(required = false) AddProductToCollectionRequest request,
            @RequestParam(required = false) Long productId) {
        Long targetProductId = (request != null && request.productId() != null) ? request.productId() : productId;
        Integer position = (request != null) ? request.position() : null;
        if (targetProductId == null) {
            throw new com.thupo.managerShop.common.exception.BadRequestAlertException("ID sản phẩm không được để trống");
        }
        collectionService.addProductToCollection(id, targetProductId, position);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Xóa sản phẩm khỏi bộ sưu tập thủ công (MANUAL).
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER.
     */
    @DeleteMapping("/{id}/products/{productId}")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<Void> removeProductFromCollection(
            @PathVariable Long id,
            @PathVariable Long productId) {
        collectionService.removeProductFromCollection(id, productId);
        return ResponseEntity.noContent().build();
    }
}
