package com.thupo.managerShop.supplychain.service;

import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.repository.BranchRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.repository.ProductVariantRepository;
import com.thupo.managerShop.supplychain.dto.InventoryAdjustmentItem;
import com.thupo.managerShop.supplychain.dto.InventoryAdjustmentRequest;
import com.thupo.managerShop.supplychain.dto.InventoryLevelResponse;
import com.thupo.managerShop.supplychain.dto.InventoryMovementResponse;
import com.thupo.managerShop.supplychain.entity.InventoryLevel;
import com.thupo.managerShop.supplychain.entity.InventoryMovement;
import com.thupo.managerShop.supplychain.entity.MovementType;
import com.thupo.managerShop.supplychain.repository.InventoryLevelRepository;
import com.thupo.managerShop.supplychain.repository.InventoryMovementRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service trung tâm quản lý toàn bộ nghiệp vụ Tồn kho đa điểm và Sổ cái biến động.
 * Tất cả các phân hệ khác (PO, Transfers, Orders) đều phải gọi qua service này.
 */
@Service
public class InventoryService {

    private final InventoryLevelRepository inventoryLevelRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ProductVariantRepository productVariantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final InventoryMapper inventoryMapper;

    public InventoryService(InventoryLevelRepository inventoryLevelRepository,
                            InventoryMovementRepository inventoryMovementRepository,
                            ProductVariantRepository productVariantRepository,
                            BranchRepository branchRepository,
                            UserRepository userRepository,
                            InventoryMapper inventoryMapper) {
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.productVariantRepository = productVariantRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.inventoryMapper = inventoryMapper;
    }

    /**
     * Điều chỉnh số lượng tồn khả dụng (available) theo delta hoặc theo mô hình kiểm đếm (newAvailable).
     * Luôn bảo toàn đẳng thức: on_hand = available + committed + unavailable.
     */
    @Transactional
    public InventoryLevelResponse adjustAvailable(Long variantId,
                                                  Long locationId,
                                                  int delta,
                                                  String reason,
                                                  String refType,
                                                  String refId,
                                                  Long userId) {
        InventoryLevel level = getOrCreateLevelForUpdate(variantId, locationId);

        int availBefore = level.getAvailable();
        int onHandBefore = level.getOnHand();

        int availAfter = availBefore + delta;
        int onHandAfter = onHandBefore + delta;

        if (availAfter < 0 || onHandAfter < 0) {
            throw new ConflictException("Không đủ tồn kho khả dụng cho biến thể ID " + variantId
                    + " tại kho ID " + locationId + " (Hiện có: " + availBefore + ", yêu cầu trừ: " + Math.abs(delta) + ")");
        }

        level.setAvailable(availAfter);
        level.setOnHand(onHandAfter);
        InventoryLevel savedLevel = inventoryLevelRepository.save(level);

        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        InventoryMovement movement = new InventoryMovement(
                level.getVariant(),
                level.getLocation(),
                MovementType.ADJUSTMENT,
                delta,
                availBefore,
                availAfter,
                onHandBefore,
                onHandAfter,
                reason,
                refType,
                refId,
                user
        );
        inventoryMovementRepository.save(movement);

        return inventoryMapper.toLevelResponse(savedLevel);
    }

    /**
     * Nhập hàng vào kho (dùng cho PO hoặc Transfers hoàn về).
     * Tăng available và on_hand, đồng thời trừ incoming nếu có.
     */
    @Transactional
    public InventoryLevelResponse receiveStock(Long variantId,
                                               Long locationId,
                                               int quantity,
                                               MovementType movementType,
                                               String reason,
                                               String refType,
                                               String refId,
                                               Long userId) {
        if (quantity <= 0) {
            throw new ConflictException("Số lượng nhập kho phải lớn hơn 0");
        }

        InventoryLevel level = getOrCreateLevelForUpdate(variantId, locationId);

        int availBefore = level.getAvailable();
        int onHandBefore = level.getOnHand();

        int availAfter = availBefore + quantity;
        int onHandAfter = onHandBefore + quantity;

        // Giảm incoming tương ứng nếu hàng đang trên đường về
        if (level.getIncoming() >= quantity) {
            level.setIncoming(level.getIncoming() - quantity);
        } else {
            level.setIncoming(0);
        }

        level.setAvailable(availAfter);
        level.setOnHand(onHandAfter);
        InventoryLevel savedLevel = inventoryLevelRepository.save(level);

        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        InventoryMovement movement = new InventoryMovement(
                level.getVariant(),
                level.getLocation(),
                movementType != null ? movementType : MovementType.PO_RECEIVE,
                quantity,
                availBefore,
                availAfter,
                onHandBefore,
                onHandAfter,
                reason,
                refType,
                refId,
                user
        );
        inventoryMovementRepository.save(movement);

        return inventoryMapper.toLevelResponse(savedLevel);
    }

    /**
     * Xuất kho (dùng cho Chuyển kho đi TRANSFER_OUT hoặc Bán hàng SALE).
     * Trừ available và on_hand. Trả lỗi 409 nếu available < quantity.
     */
    @Transactional
    public InventoryLevelResponse issueStock(Long variantId,
                                             Long locationId,
                                             int quantity,
                                             MovementType movementType,
                                             String reason,
                                             String refType,
                                             String refId,
                                             Long userId) {
        if (quantity <= 0) {
            throw new ConflictException("Số lượng xuất kho phải lớn hơn 0");
        }

        InventoryLevel level = getOrCreateLevelForUpdate(variantId, locationId);

        int availBefore = level.getAvailable();
        int onHandBefore = level.getOnHand();

        if (availBefore < quantity) {
            throw new ConflictException("INSUFFICIENT_STOCK: Không đủ tồn kho khả dụng để xuất (Hiện có: "
                    + availBefore + ", cần xuất: " + quantity + ")");
        }

        int availAfter = availBefore - quantity;
        int onHandAfter = onHandBefore - quantity;

        level.setAvailable(availAfter);
        level.setOnHand(onHandAfter);
        InventoryLevel savedLevel = inventoryLevelRepository.save(level);

        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        InventoryMovement movement = new InventoryMovement(
                level.getVariant(),
                level.getLocation(),
                movementType != null ? movementType : MovementType.TRANSFER_OUT,
                -quantity,
                availBefore,
                availAfter,
                onHandBefore,
                onHandAfter,
                reason,
                refType,
                refId,
                user
        );
        inventoryMovementRepository.save(movement);

        return inventoryMapper.toLevelResponse(savedLevel);
    }

    /**
     * Tăng số lượng hàng đang trên đường về kho (incoming).
     */
    @Transactional
    public void reserveIncoming(Long variantId, Long locationId, int quantity, String refType, String refId) {
        if (quantity <= 0) return;
        InventoryLevel level = getOrCreateLevelForUpdate(variantId, locationId);
        level.setIncoming(level.getIncoming() + quantity);
        inventoryLevelRepository.save(level);
    }

    /**
     * Giảm số lượng hàng đang trên đường về kho (incoming).
     */
    @Transactional
    public void releaseIncoming(Long variantId, Long locationId, int quantity, String refType, String refId) {
        if (quantity <= 0) return;
        InventoryLevel level = getOrCreateLevelForUpdate(variantId, locationId);
        level.setIncoming(Math.max(0, level.getIncoming() - quantity));
        inventoryLevelRepository.save(level);
    }

    /**
     * Điều chỉnh tồn kho theo lô (Batch Adjustment) trong cùng một Transaction.
     * Nếu 1 dòng lỗi thì toàn bộ lô sẽ bị rollback.
     */
    @Transactional
    public List<InventoryLevelResponse> adjustBatch(InventoryAdjustmentRequest request, Long userId) {
        List<InventoryLevelResponse> results = new ArrayList<>();
        for (InventoryAdjustmentItem item : request.adjustments()) {
            int delta;
            if (item.newAvailable() != null) {
                // Mô hình kiểm đếm: newAvailable
                InventoryLevel current = getOrCreateLevelForUpdate(item.variantId(), item.locationId());
                delta = item.newAvailable() - current.getAvailable();
            } else if (item.delta() != null) {
                delta = item.delta();
            } else {
                throw new ConflictException("Cần cung cấp ít nhất delta hoặc newAvailable cho mỗi dòng điều chỉnh");
            }

            InventoryLevelResponse res = adjustAvailable(
                    item.variantId(),
                    item.locationId(),
                    delta,
                    item.reason(),
                    request.referenceType() != null ? request.referenceType() : "ADJUSTMENT",
                    request.referenceId(),
                    userId
            );
            results.add(res);
        }
        return results;
    }

    /**
     * Danh sách tồn kho có lọc và phân trang.
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<InventoryLevelResponse>> getInventory(Long locationId,
                                                                  Long variantId,
                                                                  Long productId,
                                                                  String q,
                                                                  Integer lowStockThreshold,
                                                                  int page,
                                                                  int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<InventoryLevel> levelPage = inventoryLevelRepository.searchInventory(
                locationId, variantId, productId, (q != null && !q.isBlank()) ? q.trim() : null, lowStockThreshold, pageable);

        List<InventoryLevelResponse> dtos = levelPage.getContent().stream()
                .map(inventoryMapper::toLevelResponse)
                .toList();

        PageMeta meta = new PageMeta(
                levelPage.getNumber(),
                levelPage.getSize(),
                levelPage.getTotalElements(),
                levelPage.getTotalPages()
        );

        return ApiResponse.of(dtos, meta);
    }

    /**
     * Lịch sử biến động sổ cái kho (movements).
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<InventoryMovementResponse>> getMovements(Long variantId,
                                                                     Long locationId,
                                                                     Instant from,
                                                                     Instant to,
                                                                     int page,
                                                                     int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);

        Page<InventoryMovement> movPage = inventoryMovementRepository.searchMovements(
                variantId, locationId, from, to, pageable);

        List<InventoryMovementResponse> dtos = movPage.getContent().stream()
                .map(inventoryMapper::toMovementResponse)
                .toList();

        PageMeta meta = new PageMeta(
                movPage.getNumber(),
                movPage.getSize(),
                movPage.getTotalElements(),
                movPage.getTotalPages()
        );

        return ApiResponse.of(dtos, meta);
    }

    /**
     * Tính tổng tồn kho khả dụng của sản phẩm trên mọi chi nhánh (dùng cho Product 5A).
     */
    @Transactional(readOnly = true)
    public int getTotalAvailableForProduct(Long productId) {
        return inventoryLevelRepository.sumAvailableByProductId(productId);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private InventoryLevel getOrCreateLevelForUpdate(Long variantId, Long locationId) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với id: " + variantId));

        if (!variant.isInventoryTracked()) {
            throw new ConflictException("Biến thể ID " + variantId + " không được cấu hình theo dõi tồn kho (inventory_tracked=false)");
        }

        Branch location = branchRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh/kho với id: " + locationId));

        var levelOpt = inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(variantId, locationId);
        if (levelOpt.isPresent()) {
            return levelOpt.get();
        }

        // Tự tạo mới dòng inventory_levels với toàn số 0 nếu chưa có
        InventoryLevel newLevel = new InventoryLevel(variant, location);
        InventoryLevel saved = inventoryLevelRepository.saveAndFlush(newLevel);
        return inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(variantId, locationId)
                .orElse(saved);
    }
}
