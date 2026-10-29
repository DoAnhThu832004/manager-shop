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
import com.thupo.managerShop.supplychain.dto.InventoryTransferCreateRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferItemRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferReceiveItemRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferReceiveRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferResponse;
import com.thupo.managerShop.supplychain.dto.InventoryTransferUpdateRequest;
import com.thupo.managerShop.supplychain.entity.InventoryLevel;
import com.thupo.managerShop.supplychain.entity.InventoryTransfer;
import com.thupo.managerShop.supplychain.entity.InventoryTransferItem;
import com.thupo.managerShop.supplychain.entity.InventoryTransferReceipt;
import com.thupo.managerShop.supplychain.entity.InventoryTransferReceiptItem;
import com.thupo.managerShop.supplychain.entity.MovementType;
import com.thupo.managerShop.supplychain.entity.PurchaseOrder;
import com.thupo.managerShop.supplychain.entity.TransferStatus;
import com.thupo.managerShop.supplychain.repository.InventoryLevelRepository;
import com.thupo.managerShop.supplychain.repository.InventoryTransferItemRepository;
import com.thupo.managerShop.supplychain.repository.InventoryTransferReceiptRepository;
import com.thupo.managerShop.supplychain.repository.InventoryTransferRepository;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderRepository;
import java.time.Instant;
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
 * Service quản lý toàn bộ vòng đời và luân chuyển tồn kho (Inventory Transfer).
 */
@Service
public class InventoryTransferService {

    private final InventoryTransferRepository inventoryTransferRepository;
    private final InventoryTransferItemRepository inventoryTransferItemRepository;
    private final InventoryTransferReceiptRepository inventoryTransferReceiptRepository;
    private final BranchRepository branchRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final InventoryService inventoryService;
    private final TransferMapper transferMapper;

    public InventoryTransferService(InventoryTransferRepository inventoryTransferRepository,
                                    InventoryTransferItemRepository inventoryTransferItemRepository,
                                    InventoryTransferReceiptRepository inventoryTransferReceiptRepository,
                                    BranchRepository branchRepository,
                                    ProductVariantRepository productVariantRepository,
                                    PurchaseOrderRepository purchaseOrderRepository,
                                    UserRepository userRepository,
                                    InventoryLevelRepository inventoryLevelRepository,
                                    InventoryService inventoryService,
                                    TransferMapper transferMapper) {
        this.inventoryTransferRepository = inventoryTransferRepository;
        this.inventoryTransferItemRepository = inventoryTransferItemRepository;
        this.inventoryTransferReceiptRepository = inventoryTransferReceiptRepository;
        this.branchRepository = branchRepository;
        this.productVariantRepository = productVariantRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.userRepository = userRepository;
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.inventoryService = inventoryService;
        this.transferMapper = transferMapper;
    }

    /**
     * Tạo mới đơn chuyển kho ở trạng thái DRAFT.
     */
    @Transactional
    public InventoryTransferResponse createTransfer(InventoryTransferCreateRequest request) {
        if (request.originLocationId().equals(request.destinationLocationId())) {
            throw new BadRequestAlertException("Kho nguồn và kho đích không được trùng nhau");
        }

        Branch origin = branchRepository.findById(request.originLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho nguồn với id: " + request.originLocationId()));

        Branch destination = branchRepository.findById(request.destinationLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho đích với id: " + request.destinationLocationId()));

        PurchaseOrder linkedPo = null;
        if (request.linkedPurchaseOrderId() != null) {
            linkedPo = purchaseOrderRepository.findById(request.linkedPurchaseOrderId()).orElse(null);
        }

        Long nextId = inventoryTransferRepository.findMaxId() + 1;
        String transferNumber = String.format("TR-%06d", nextId);

        InventoryTransfer transfer = new InventoryTransfer(transferNumber, origin, destination);
        transfer.setTransferDate(request.transferDate());
        transfer.setReferenceName(request.referenceName());
        transfer.setNote(request.note());
        transfer.setTags(transferMapper.serializeTags(request.tags()));
        transfer.setLinkedPurchaseOrder(linkedPo);
        transfer.setStatus(TransferStatus.DRAFT);

        Set<Long> seenVariants = new HashSet<>();
        for (InventoryTransferItemRequest itemReq : request.items()) {
            if (!seenVariants.add(itemReq.variantId())) {
                throw new ConflictException("Biến thể ID " + itemReq.variantId() + " bị lặp lại trong danh sách chuyển kho");
            }

            ProductVariant variant = productVariantRepository.findById(itemReq.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + itemReq.variantId()));

            if (!variant.isInventoryTracked()) {
                throw new ConflictException("Biến thể " + variant.getTitle() + " không được cấu hình theo dõi tồn kho (inventory_tracked=false)");
            }

            InventoryTransferItem item = new InventoryTransferItem(transfer, variant, itemReq.quantityTransferred());
            transfer.getItems().add(item);
        }

        InventoryTransfer saved = inventoryTransferRepository.save(transfer);
        return transferMapper.toTransferResponse(saved);
    }

    /**
     * Cập nhật đơn chuyển kho (chỉ cho phép khi ở trạng thái DRAFT).
     */
    @Transactional
    public InventoryTransferResponse updateTransfer(Long id, InventoryTransferUpdateRequest request) {
        InventoryTransfer transfer = inventoryTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn chuyển kho với id: " + id));

        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new ConflictException("Chỉ có thể chỉnh sửa đơn chuyển kho ở trạng thái DRAFT (Hiện tại: " + transfer.getStatus() + ")");
        }

        if (request.originLocationId().equals(request.destinationLocationId())) {
            throw new BadRequestAlertException("Kho nguồn và kho đích không được trùng nhau");
        }

        Branch origin = branchRepository.findById(request.originLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho nguồn với id: " + request.originLocationId()));

        Branch destination = branchRepository.findById(request.destinationLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho đích với id: " + request.destinationLocationId()));

        PurchaseOrder linkedPo = null;
        if (request.linkedPurchaseOrderId() != null) {
            linkedPo = purchaseOrderRepository.findById(request.linkedPurchaseOrderId()).orElse(null);
        }

        transfer.setOriginLocation(origin);
        transfer.setDestinationLocation(destination);
        transfer.setTransferDate(request.transferDate());
        transfer.setReferenceName(request.referenceName());
        transfer.setNote(request.note());
        transfer.setTags(transferMapper.serializeTags(request.tags()));
        transfer.setLinkedPurchaseOrder(linkedPo);

        transfer.getItems().clear();
        Set<Long> seenVariants = new HashSet<>();
        for (InventoryTransferItemRequest itemReq : request.items()) {
            if (!seenVariants.add(itemReq.variantId())) {
                throw new ConflictException("Biến thể ID " + itemReq.variantId() + " bị lặp lại trong danh sách chuyển kho");
            }

            ProductVariant variant = productVariantRepository.findById(itemReq.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + itemReq.variantId()));

            if (!variant.isInventoryTracked()) {
                throw new ConflictException("Biến thể " + variant.getTitle() + " không được cấu hình theo dõi tồn kho (inventory_tracked=false)");
            }

            InventoryTransferItem item = new InventoryTransferItem(transfer, variant, itemReq.quantityTransferred());
            transfer.getItems().add(item);
        }

        InventoryTransfer saved = inventoryTransferRepository.save(transfer);
        return transferMapper.toTransferResponse(saved);
    }

    /**
     * Gửi hàng chuyển kho: DRAFT -> IN_TRANSIT.
     * Kiểm tra kho nguồn đủ hàng (available >= quantity).
     * Trừ kho nguồn bằng issueStock, tăng incoming ở kho đích bằng reserveIncoming.
     * Bảo toàn: Tổng (on_hand nguồn + on_hand đích + incoming đích) trước và sau không đổi!
     */
    @Transactional
    public InventoryTransferResponse sendTransfer(Long id, Long userId) {
        InventoryTransfer transfer = inventoryTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn chuyển kho với id: " + id));

        if (transfer.getStatus() != TransferStatus.DRAFT) {
            throw new ConflictException("Chỉ đơn chuyển kho ở trạng thái DRAFT mới có thể gửi hàng (IN_TRANSIT). Hiện tại: " + transfer.getStatus());
        }

        Long originId = transfer.getOriginLocation().getId();
        Long destinationId = transfer.getDestinationLocation().getId();

        // 1. Tiền kiểm tra: Kiểm tra tồn kho khả dụng tại kho nguồn cho TẤT CẢ các dòng
        for (InventoryTransferItem item : transfer.getItems()) {
            Long variantId = item.getVariant().getId();
            InventoryLevel originLevel = inventoryLevelRepository.findByVariantIdAndLocationId(variantId, originId)
                    .orElse(null);

            int availableStock = (originLevel != null) ? originLevel.getAvailable() : 0;
            if (availableStock < item.getQuantityTransferred()) {
                throw new ConflictException("INSUFFICIENT_STOCK: Kho nguồn không đủ tồn kho khả dụng cho biến thể: "
                        + item.getVariant().getTitle() + " (Hiện có: " + availableStock
                        + ", cần chuyển: " + item.getQuantityTransferred() + ")");
            }
        }

        // 2. Thực hiện trừ kho nguồn và tăng incoming ở kho đích
        for (InventoryTransferItem item : transfer.getItems()) {
            Long variantId = item.getVariant().getId();
            int qty = item.getQuantityTransferred();

            // Trừ available và on_hand ở kho nguồn
            inventoryService.issueStock(
                    variantId,
                    originId,
                    qty,
                    MovementType.TRANSFER_OUT,
                    "Xuất chuyển kho " + transfer.getTransferNumber(),
                    "TRANSFER",
                    transfer.getTransferNumber(),
                    userId
            );

            // Tăng incoming ở kho đích (chưa tăng available)
            inventoryService.reserveIncoming(
                    variantId,
                    destinationId,
                    qty,
                    "TRANSFER",
                    transfer.getTransferNumber()
            );
        }

        transfer.setStatus(TransferStatus.IN_TRANSIT);
        InventoryTransfer saved = inventoryTransferRepository.save(transfer);
        return transferMapper.toTransferResponse(saved);
    }

    /**
     * Nhận hàng chuyển kho (hỗ trợ nhận 1 phần và Idempotency).
     * Kho đích: giảm incoming, tăng available và on_hand qua receiveStock.
     */
    @Transactional
    public InventoryTransferResponse receiveTransfer(Long id, InventoryTransferReceiveRequest request, Long userId) {
        var existingReceiptOpt = inventoryTransferReceiptRepository.findByIdempotencyKey(request.idempotencyKey().trim());
        if (existingReceiptOpt.isPresent()) {
            return transferMapper.toTransferResponse(existingReceiptOpt.get().getTransfer());
        }

        InventoryTransfer transfer = inventoryTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn chuyển kho với id: " + id));

        if (transfer.getStatus() != TransferStatus.IN_TRANSIT && transfer.getStatus() != TransferStatus.PARTIALLY_RECEIVED) {
            throw new ConflictException("Không thể nhận hàng cho đơn chuyển kho ở trạng thái: " + transfer.getStatus()
                    + " (Chỉ cho phép khi IN_TRANSIT hoặc PARTIALLY_RECEIVED)");
        }

        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        InventoryTransferReceipt receipt = new InventoryTransferReceipt(
                transfer,
                user,
                request.idempotencyKey().trim(),
                request.note()
        );

        // Kiểm tra trước số lượng nhận không vượt quá số lượng chuyển
        for (InventoryTransferReceiveItemRequest reqItem : request.items()) {
            InventoryTransferItem item = transfer.getItems().stream()
                    .filter(i -> i.getId().equals(reqItem.itemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng chuyển kho ID: " + reqItem.itemId()
                            + " trong đơn " + transfer.getTransferNumber()));

            int newTotalReceived = item.getQuantityReceived() + reqItem.quantity();
            if (newTotalReceived > item.getQuantityTransferred()) {
                throw new ConflictException("Số lượng nhận (" + newTotalReceived + ") vượt quá số lượng cần chuyển ("
                        + item.getQuantityTransferred() + ") cho biến thể: " + item.getVariant().getTitle());
            }
        }

        // Thực hiện nhập kho đích
        Long destinationId = transfer.getDestinationLocation().getId();
        for (InventoryTransferReceiveItemRequest reqItem : request.items()) {
            InventoryTransferItem item = transfer.getItems().stream()
                    .filter(i -> i.getId().equals(reqItem.itemId()))
                    .findFirst()
                    .orElseThrow();

            item.setQuantityReceived(item.getQuantityReceived() + reqItem.quantity());

            // Nhập kho đích: giảm incoming, tăng available & on_hand, ghi movement TRANSFER_IN
            inventoryService.receiveStock(
                    item.getVariant().getId(),
                    destinationId,
                    reqItem.quantity(),
                    MovementType.TRANSFER_IN,
                    request.note() != null ? request.note() : "Nhận hàng chuyển kho " + transfer.getTransferNumber(),
                    "TRANSFER",
                    transfer.getTransferNumber(),
                    userId
            );

            InventoryTransferReceiptItem receiptItem = new InventoryTransferReceiptItem(receipt, item, reqItem.quantity());
            receipt.getReceiptItems().add(receiptItem);
        }

        boolean allFullyReceived = transfer.getItems().stream()
                .allMatch(i -> i.getQuantityReceived() >= i.getQuantityTransferred());

        transfer.setStatus(allFullyReceived ? TransferStatus.COMPLETED : TransferStatus.PARTIALLY_RECEIVED);
        transfer.getReceipts().add(receipt);

        inventoryTransferReceiptRepository.save(receipt);
        InventoryTransfer saved = inventoryTransferRepository.save(transfer);
        return transferMapper.toTransferResponse(saved);
    }

    /**
     * Hủy đơn chuyển kho:
     * - DRAFT: Chuyển sang CANCELLED, không đụng tồn kho.
     * - IN_TRANSIT chưa nhận gì: Hoàn trả lại kho nguồn qua receiveStock, giải phóng incoming ở kho đích qua releaseIncoming.
     * - PARTIALLY_RECEIVED hoặc COMPLETED: Không cho phép hủy (trả 409).
     */
    @Transactional
    public InventoryTransferResponse cancelTransfer(Long id, Long userId) {
        InventoryTransfer transfer = inventoryTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn chuyển kho với id: " + id));

        if (transfer.getStatus() == TransferStatus.DRAFT) {
            transfer.setStatus(TransferStatus.CANCELLED);
        } else if (transfer.getStatus() == TransferStatus.IN_TRANSIT) {
            boolean anyReceived = transfer.getItems().stream().anyMatch(i -> i.getQuantityReceived() > 0);
            if (anyReceived) {
                throw new ConflictException("Không thể hủy đơn chuyển kho đã nhận hàng một phần (PARTIALLY_RECEIVED)");
            }

            Long originId = transfer.getOriginLocation().getId();
            Long destinationId = transfer.getDestinationLocation().getId();

            for (InventoryTransferItem item : transfer.getItems()) {
                Long variantId = item.getVariant().getId();
                int qty = item.getQuantityTransferred();

                // Hoàn lại kho nguồn
                inventoryService.receiveStock(
                        variantId,
                        originId,
                        qty,
                        MovementType.TRANSFER_IN,
                        "Hoàn lại do hủy chuyển kho " + transfer.getTransferNumber(),
                        "TRANSFER",
                        transfer.getTransferNumber(),
                        userId
                );

                // Giảm incoming ở kho đích
                inventoryService.releaseIncoming(
                        variantId,
                        destinationId,
                        qty,
                        "TRANSFER",
                        transfer.getTransferNumber()
                );
            }

            transfer.setStatus(TransferStatus.CANCELLED);
        } else {
            throw new ConflictException("Không thể hủy đơn chuyển kho ở trạng thái: " + transfer.getStatus());
        }

        InventoryTransfer saved = inventoryTransferRepository.save(transfer);
        return transferMapper.toTransferResponse(saved);
    }

    @Transactional(readOnly = true)
    public InventoryTransferResponse getTransfer(Long id) {
        InventoryTransfer transfer = inventoryTransferRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn chuyển kho với id: " + id));
        return transferMapper.toTransferResponse(transfer);
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<InventoryTransferResponse>> getTransfers(TransferStatus status,
                                                                     Long originLocationId,
                                                                     Long destinationLocationId,
                                                                     Instant from,
                                                                     Instant to,
                                                                     String q,
                                                                     int page,
                                                                     int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<InventoryTransfer> transferPage = inventoryTransferRepository.searchTransfers(
                status, originLocationId, destinationLocationId, from, to,
                (q != null && !q.isBlank()) ? q.trim() : null,
                pageable
        );

        List<InventoryTransferResponse> dtos = transferPage.getContent().stream()
                .map(transferMapper::toTransferResponse)
                .toList();

        PageMeta meta = new PageMeta(
                transferPage.getNumber(),
                transferPage.getSize(),
                transferPage.getTotalElements(),
                transferPage.getTotalPages()
        );

        return ApiResponse.of(dtos, meta);
    }
}
