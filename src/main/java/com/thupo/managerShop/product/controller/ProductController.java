package com.thupo.managerShop.product.controller;

import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.security.RequirePermission;
import com.thupo.managerShop.product.dto.ProductCreateRequest;
import com.thupo.managerShop.product.dto.ProductListResponse;
import com.thupo.managerShop.product.dto.ProductResponse;
import com.thupo.managerShop.product.dto.ProductUpdateRequest;
import com.thupo.managerShop.product.dto.SavedViewsAndColumnsResponse;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.service.ProductService;
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
 * REST Controller cho Module Sản phẩm.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Danh sách sản phẩm có tìm kiếm, lọc và phân trang.
     * Cho phép mọi tài khoản đã đăng nhập (Staff, Admin, Owner...) truy cập GET.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductListResponse>>> getProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        ApiResponse<List<ProductListResponse>> response = productService.getProducts(
                q, status, vendor, productType, tag, page, size, sort);
        return ResponseEntity.ok(response);
    }

    /**
     * Alias endpoint theo hợp đồng HAR / openapi.yaml: /api/v1/products/product-index.
     */
    @GetMapping("/product-index")
    public ResponseEntity<ApiResponse<List<ProductListResponse>>> getProductIndex(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return getProducts(q, status, vendor, productType, tag, page, size, sort);
    }

    /**
     * Lấy cấu hình cột và bộ lọc mặc định (SavedViewsAndColumns).
     */
    @GetMapping("/saved-views-and-columns")
    public ResponseEntity<ApiResponse<SavedViewsAndColumnsResponse>> getSavedViewsAndColumns() {
        SavedViewsAndColumnsResponse views = productService.getSavedViewsAndColumns();
        return ResponseEntity.ok(ApiResponse.of(views));
    }

    /**
     * Chi tiết sản phẩm theo ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        ProductResponse response = productService.getProduct(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * Tạo mới sản phẩm.
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER. STAFF bị chặn 403.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductCreateRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    /**
     * Cập nhật toàn bộ thông tin sản phẩm.
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER. STAFF bị chặn 403.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * Xóa mềm sản phẩm.
     * Quyền: Cần quyền MANAGE_PRODUCTS hoặc vai trò ADMIN/OWNER. STAFF bị chặn 403.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_PRODUCTS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_PRODUCTS")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
