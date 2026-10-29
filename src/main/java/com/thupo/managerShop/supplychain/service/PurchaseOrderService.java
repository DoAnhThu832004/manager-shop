package com.thupo.managerShop.supplychain.service;

import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.repository.BranchRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.repository.ProductVariantRepository;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderCreateRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderItemRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiveItemRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiveRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderResponse;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderUpdateRequest;
import com.thupo.managerShop.supplychain.entity.MovementType;
import com.thupo.managerShop.supplychain.entity.PurchaseOrder;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderItem;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderReceipt;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderReceiptItem;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderStatus;
import com.thupo.managerShop.supplychain.entity.Supplier;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderItemRepository;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderReceiptRepository;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderRepository;
import com.thupo.managerShop.supplychain.repository.SupplierRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý toàn bộ vòng đời Đơn đặt hàng nhập (Purchase Orders) và Phiếu nhận hàng (Receipts).
 */
@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderReceiptRepository purchaseOrderReceiptRepository;
    private final SupplierRepository supplierRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final PurchaseOrderMapper purchaseOrderMapper;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                PurchaseOrderItemRepository purchaseOrderItemRepository,
                                PurchaseOrderReceiptRepository purchaseOrderReceiptRepository,
                                SupplierRepository supplierRepository,
                                BranchRepository branchRepository,
                                ProductVariantRepository productVariantRepository,
                                UserRepository userRepository,
                                InventoryService inventoryService,
                                PurchaseOrderMapper purchaseOrderMapper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderItemRepository = purchaseOrderItemRepository;
        this.purchaseOrderReceiptRepository = purchaseOrderReceiptRepository;
        this.supplierRepository = supplierRepository;
        this.branchRepository = branchRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    /**
     * Tạo mới đơn đặt hàng nhập ở trạng thái DRAFT.
     * Tính toán subtotal_cost, tax_total, total_cost ở server.
     */
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderCreateRequest request) {
        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với id: " + request.supplierId()));

        Branch destination = branchRepository.findById(request.destinationLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho đích với id: " + request.destinationLocationId()));

        Long nextId = purchaseOrderRepository.findMaxId() + 1;
        String poNumber = String.format("PO-%06d", nextId);

        PurchaseOrder po = new PurchaseOrder(poNumber, supplier, destination);
        po.setReferenceNumber(request.referenceNumber());
        po.setNoteToSupplier(request.noteToSupplier());
        po.setPaymentTerms(request.paymentTerms());
        po.setCurrency(request.currency() != null ? request.currency() : "VND");
        po.setTags(purchaseOrderMapper.serializeTags(request.tags()));
        po.setStatus(PurchaseOrderStatus.DRAFT);

        BigDecimal taxTotal = request.taxTotal() != null ? request.taxTotal() : BigDecimal.ZERO;
        po.setTaxTotal(taxTotal);

        Set<Long> seenVariants = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (PurchaseOrderItemRequest itemReq : request.items()) {
            if (!seenVariants.add(itemReq.variantId())) {
                throw new ConflictException("Biến thể ID " + itemReq.variantId() + " bị lặp lại trong danh sách đặt hàng");
            }

            ProductVariant variant = productVariantRepository.findById(itemReq.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + itemReq.variantId()));

            if (!variant.isInventoryTracked()) {
                throw new ConflictException("Biến thể " + variant.getTitle() + " không được cấu hình theo dõi tồn kho");
            }

            BigDecimal unitCost = itemReq.unitCost() != null ? itemReq.unitCost() : BigDecimal.ZERO;
            PurchaseOrderItem item = new PurchaseOrderItem(po, variant, itemReq.quantityOrdered(), unitCost);
            po.getItems().add(item);

            BigDecimal lineTotal = unitCost.multiply(BigDecimal.valueOf(itemReq.quantityOrdered()));
            subtotal = subtotal.add(lineTotal);
        }

        po.setSubtotalCost(subtotal);
        po.setTotalCost(subtotal.add(taxTotal));

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return purchaseOrderMapper.toPurchaseOrderResponse(saved);
    }

    /**
     * Cập nhật thông tin đơn hàng nhập (chỉ cho phép khi ở trạng thái DRAFT).
     */
    @Transactional
    public PurchaseOrderResponse updatePurchaseOrder(Long id, PurchaseOrderUpdateRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt hàng với id: " + id));

        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException("Chỉ có thể chỉnh sửa đơn đặt hàng ở trạng thái DRAFT (Hiện tại: " + po.getStatus() + ")");
        }

        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với id: " + request.supplierId()));

        Branch destination = branchRepository.findById(request.destinationLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho đích với id: " + request.destinationLocationId()));

        po.setSupplier(supplier);
        po.setDestinationLocation(destination);
        po.setReferenceNumber(request.referenceNumber());
        po.setNoteToSupplier(request.noteToSupplier());
        po.setPaymentTerms(request.paymentTerms());
        po.setCurrency(request.currency() != null ? request.currency() : "VND");
        po.setTags(purchaseOrderMapper.serializeTags(request.tags()));

        BigDecimal taxTotal = request.taxTotal() != null ? request.taxTotal() : BigDecimal.ZERO;
        po.setTaxTotal(taxTotal);

        po.getItems().clear();
        Set<Long> seenVariants = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (PurchaseOrderItemRequest itemReq : request.items()) {
            if (!seenVariants.add(itemReq.variantId())) {
                throw new ConflictException("Biến thể ID " + itemReq.variantId() + " bị lặp lại trong danh sách đặt hàng");
            }

            ProductVariant variant = productVariantRepository.findById(itemReq.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + itemReq.variantId()));

            if (!variant.isInventoryTracked()) {
                throw new ConflictException("Biến thể " + variant.getTitle() + " không được cấu hình theo dõi tồn kho");
            }

            BigDecimal unitCost = itemReq.unitCost() != null ? itemReq.unitCost() : BigDecimal.ZERO;
            PurchaseOrderItem item = new PurchaseOrderItem(po, variant, itemReq.quantityOrdered(), unitCost);
            po.getItems().add(item);

            BigDecimal lineTotal = unitCost.multiply(BigDecimal.valueOf(itemReq.quantityOrdered()));
            subtotal = subtotal.add(lineTotal);
        }

        po.setSubtotalCost(subtotal);
        po.setTotalCost(subtotal.add(taxTotal));

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return purchaseOrderMapper.toPurchaseOrderResponse(saved);
    }

    /**
     * Chốt đơn đặt hàng nhập: DRAFT -> ORDERED.
     * Khóa dòng hàng, cộng incoming ở kho đích thông qua InventoryService.reserveIncoming.
     */
    @Transactional
    public PurchaseOrderResponse orderPurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt hàng với id: " + id));

        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ConflictException("Chỉ đơn hàng ở trạng thái DRAFT mới có thể đặt hàng (ORDERED). Trạng thái hiện tại: " + po.getStatus());
        }

        Long destinationId = po.getDestinationLocation().getId();
        for (PurchaseOrderItem item : po.getItems()) {
            inventoryService.reserveIncoming(
                    item.getVariant().getId(),
                    destinationId,
                    item.getQuantityOrdered(),
                    "PURCHASE_ORDER",
                    po.getPoNumber()
            );
        }

        po.setStatus(PurchaseOrderStatus.ORDERED);
        po.setOrderedAt(Instant.now());

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return purchaseOrderMapper.toPurchaseOrderResponse(saved);
    }

    /**
     * Nhận hàng thực tế (cho phép nhận một phần hoặc toàn bộ).
     * Đảm bảo Idempotency: gửi lại cùng idempotencyKey trả lại kết quả cũ, không cộng tồn kho lần nữa.
     */
    @Transactional
    public PurchaseOrderResponse receivePurchaseOrder(Long id, PurchaseOrderReceiveRequest request, Long userId) {
        var existingReceiptOpt = purchaseOrderReceiptRepository.findByIdempotencyKey(request.idempotencyKey().trim());
        if (existingReceiptOpt.isPresent()) {
            // Trả lại dữ liệu cũ idempotent
            return purchaseOrderMapper.toPurchaseOrderResponse(existingReceiptOpt.get().getPurchaseOrder());
        }

        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt hàng với id: " + id));

        if (po.getStatus() != PurchaseOrderStatus.ORDERED && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ConflictException("Không thể nhận hàng cho đơn ở trạng thái: " + po.getStatus()
                    + " (Chỉ cho phép khi ORDERED hoặc PARTIALLY_RECEIVED)");
        }

        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        PurchaseOrderReceipt receipt = new PurchaseOrderReceipt(
                po,
                user,
                request.idempotencyKey().trim(),
                request.note()
        );

        // Kiểm tra trước toàn bộ các dòng xem có vượt quá số lượng đặt không
        for (PurchaseOrderReceiveItemRequest reqItem : request.items()) {
            PurchaseOrderItem item = po.getItems().stream()
                    .filter(i -> i.getId().equals(reqItem.itemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng đặt hàng ID: " + reqItem.itemId()
                            + " trong đơn " + po.getPoNumber()));

            int newTotalReceived = item.getQuantityReceived() + reqItem.quantity();
            if (newTotalReceived > item.getQuantityOrdered()) {
                throw new ConflictException("Số lượng nhận (" + newTotalReceived + ") vượt quá số lượng đặt ("
                        + item.getQuantityOrdered() + ") cho biến thể: " + item.getVariant().getTitle());
            }
        }

        // Thực hiện nhập kho và cập nhật dòng hàng
        Long destinationId = po.getDestinationLocation().getId();
        for (PurchaseOrderReceiveItemRequest reqItem : request.items()) {
            PurchaseOrderItem item = po.getItems().stream()
                    .filter(i -> i.getId().equals(reqItem.itemId()))
                    .findFirst()
                    .orElseThrow();

            item.setQuantityReceived(item.getQuantityReceived() + reqItem.quantity());

            // Nhập kho: tăng available & on_hand, giảm incoming tương ứng, ghi movement PO_RECEIVE
            inventoryService.receiveStock(
                    item.getVariant().getId(),
                    destinationId,
                    reqItem.quantity(),
                    MovementType.PO_RECEIVE,
                    request.note() != null ? request.note() : "Nhận hàng theo đơn " + po.getPoNumber(),
                    "PURCHASE_ORDER",
                    po.getPoNumber(),
                    userId
            );

            PurchaseOrderReceiptItem receiptItem = new PurchaseOrderReceiptItem(receipt, item, reqItem.quantity());
            receipt.getReceiptItems().add(receiptItem);
        }

        // Cập nhật trạng thái đơn hàng
        boolean allFullyReceived = po.getItems().stream()
                .allMatch(i -> i.getQuantityReceived() >= i.getQuantityOrdered());

        po.setStatus(allFullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        po.getReceipts().add(receipt);

        purchaseOrderReceiptRepository.save(receipt);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return purchaseOrderMapper.toPurchaseOrderResponse(saved);
    }

    /**
     * Đóng đơn đặt hàng nhập: Chỉ khi RECEIVED, hoặc PARTIALLY_RECEIVED mà người dùng chủ động đóng.
     * Phần chưa về thì releaseIncoming.
     */
    @Transactional
    public PurchaseOrderResponse closePurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt hàng với id: " + id));

        if (po.getStatus() != PurchaseOrderStatus.RECEIVED && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new ConflictException("Chỉ có thể đóng đơn hàng khi đã RECEIVED hoặc PARTIALLY_RECEIVED (Hiện tại: " + po.getStatus() + ")");
        }

        if (po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            Long destinationId = po.getDestinationLocation().getId();
            for (PurchaseOrderItem item : po.getItems()) {
                int unfulfilled = item.getQuantityOrdered() - item.getQuantityReceived();
                if (unfulfilled > 0) {
                    inventoryService.releaseIncoming(
                            item.getVariant().getId(),
                            destinationId,
                            unfulfilled,
                            "PURCHASE_ORDER",
                            po.getPoNumber()
                    );
                }
            }
        }

        po.setStatus(PurchaseOrderStatus.CLOSED);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return purchaseOrderMapper.toPurchaseOrderResponse(saved);
    }

    /**
     * Hủy đơn đặt hàng nhập: Cho phép khi DRAFT hoặc ORDERED chưa nhận gì.
     * Nếu ORDERED thì releaseIncoming toàn bộ phần chưa nhận.
     */
    @Transactional
    public PurchaseOrderResponse cancelPurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt hàng với id: " + id));

        if (po.getStatus() == PurchaseOrderStatus.DRAFT) {
            po.setStatus(PurchaseOrderStatus.CANCELLED);
        } else if (po.getStatus() == PurchaseOrderStatus.ORDERED) {
            boolean anyReceived = po.getItems().stream().anyMatch(i -> i.getQuantityReceived() > 0);
            if (anyReceived) {
                throw new ConflictException("Đơn hàng đã nhận một phần không thể hủy, vui lòng dùng chức năng Đóng đơn (CLOSE)");
            }

            Long destinationId = po.getDestinationLocation().getId();
            for (PurchaseOrderItem item : po.getItems()) {
                inventoryService.releaseIncoming(
                        item.getVariant().getId(),
                        destinationId,
                        item.getQuantityOrdered(),
                        "PURCHASE_ORDER",
                        po.getPoNumber()
                );
            }
            po.setStatus(PurchaseOrderStatus.CANCELLED);
        } else {
            throw new ConflictException("Không thể hủy đơn đặt hàng ở trạng thái: " + po.getStatus());
        }

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return purchaseOrderMapper.toPurchaseOrderResponse(saved);
    }

    /**
     * Lấy chi tiết đơn đặt hàng nhập kèm items và receipts.
     */
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn đặt hàng với id: " + id));
        return purchaseOrderMapper.toPurchaseOrderResponse(po);
    }

    /**
     * Danh sách đơn đặt hàng nhập có lọc và phân trang.
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<PurchaseOrderResponse>> getPurchaseOrders(PurchaseOrderStatus status,
                                                                      Long supplierId,
                                                                      Long destinationLocationId,
                                                                      Instant from,
                                                                      Instant to,
                                                                      String q,
                                                                      int page,
                                                                      int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PurchaseOrder> poPage = purchaseOrderRepository.searchPurchaseOrders(
                status,
                supplierId,
                destinationLocationId,
                from,
                to,
                (q != null && !q.isBlank()) ? q.trim() : null,
                pageable
        );

        List<PurchaseOrderResponse> dtos = poPage.getContent().stream()
                .map(purchaseOrderMapper::toPurchaseOrderResponse)
                .toList();

        PageMeta meta = new PageMeta(
                poPage.getNumber(),
                poPage.getSize(),
                poPage.getTotalElements(),
                poPage.getTotalPages()
        );

        return ApiResponse.of(dtos, meta);
    }
}
