package com.thupo.managerShop.giftcard.controller;

import com.thupo.managerShop.auth.security.AuthenticatedUser;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.security.RequirePermission;
import com.thupo.managerShop.giftcard.dto.GiftCardCreateRequest;
import com.thupo.managerShop.giftcard.dto.GiftCardCreatedResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardUpdateRequest;
import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import com.thupo.managerShop.giftcard.service.GiftCardService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * REST Controller cho Module Thẻ quà tặng (Gift Cards).
 */
@RestController
@RequestMapping("/api/v1/gift-cards")
public class GiftCardController {

    private final GiftCardService giftCardService;

    public GiftCardController(GiftCardService giftCardService) {
        this.giftCardService = giftCardService;
    }

    /**
     * Tra cứu danh sách thẻ quà tặng có lọc theo trạng thái, khách hàng, 4 số cuối và phân trang.
     * Cho phép mọi người dùng đã đăng nhập (ADMIN, STAFF).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GiftCardResponse>>> getGiftCards(
            @RequestParam(required = false) GiftCardStatus status,
            @RequestParam(name = "customer_id", required = false) Long customerId,
            @RequestParam(required = false) String last4,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        ApiResponse<List<GiftCardResponse>> response = giftCardService.getGiftCards(
                status, customerId, last4, page, size, sort);
        return ResponseEntity.ok(response);
    }

    /**
     * Alias endpoint theo hợp đồng OpenAPI / HAR: /api/v1/gift-cards/gift-card-list
     */
    @GetMapping("/gift-card-list")
    public ResponseEntity<ApiResponse<List<GiftCardResponse>>> getGiftCardsAlias(
            @RequestParam(required = false) GiftCardStatus status,
            @RequestParam(name = "customer_id", required = false) Long customerId,
            @RequestParam(required = false) String last4,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return getGiftCards(status, customerId, last4, page, size, sort);
    }

    /**
     * Chi tiết thẻ quà tặng kèm số dư và lịch sử giao dịch.
     * Chỉ trả về maskedCode (* * * * * * * * * * * * a b 1 2).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GiftCardResponse>> getGiftCard(@PathVariable Long id) {
        GiftCardResponse response = giftCardService.getGiftCard(id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    /**
     * Tạo mới thẻ quà tặng.
     * Trả về HTTP 201 Created và mã thẻ gốc DUY NHẤT trong response này.
     * Yêu cầu quyền MANAGE_GIFT_CARDS.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_GIFT_CARDS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_GIFT_CARDS")
    public ResponseEntity<ApiResponse<GiftCardCreatedResponse>> createGiftCard(
            @Valid @RequestBody GiftCardCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = currentUser != null ? currentUser.id() : null;
        GiftCardCreatedResponse created = giftCardService.createGiftCard(request, userId);
        return ResponseEntity.created(URI.create("/api/v1/gift-cards/" + created.id()))
                .body(ApiResponse.of(created));
    }

    /**
     * Cập nhật thông tin thẻ quà tặng (notes, customerId, expiresAt). Không sửa số dư hay mã thẻ.
     * Yêu cầu quyền MANAGE_GIFT_CARDS.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_GIFT_CARDS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_GIFT_CARDS")
    public ResponseEntity<ApiResponse<GiftCardResponse>> updateGiftCard(
            @PathVariable Long id,
            @Valid @RequestBody GiftCardUpdateRequest request) {
        GiftCardResponse updated = giftCardService.updateGiftCard(id, request);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    /**
     * Vô hiệu hóa thẻ quà tặng (thao tác idempotent).
     * Yêu cầu quyền MANAGE_GIFT_CARDS.
     */
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('MANAGE_GIFT_CARDS') or hasAnyRole('ADMIN', 'OWNER')")
    @RequirePermission("MANAGE_GIFT_CARDS")
    public ResponseEntity<ApiResponse<GiftCardResponse>> disableGiftCard(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        Long userId = currentUser != null ? currentUser.id() : null;
        GiftCardResponse disabled = giftCardService.disableGiftCard(id, userId);
        return ResponseEntity.ok(ApiResponse.of(disabled));
    }
}
