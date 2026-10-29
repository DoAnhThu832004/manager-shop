package com.thupo.managerShop.supplychain.service;

import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.supplychain.dto.SupplierCreateRequest;
import com.thupo.managerShop.supplychain.dto.SupplierResponse;
import com.thupo.managerShop.supplychain.dto.SupplierUpdateRequest;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderStatus;
import com.thupo.managerShop.supplychain.entity.Supplier;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderRepository;
import com.thupo.managerShop.supplychain.repository.SupplierRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;

    public SupplierService(SupplierRepository supplierRepository,
                           PurchaseOrderRepository purchaseOrderRepository,
                           PurchaseOrderMapper purchaseOrderMapper) {
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierCreateRequest request) {
        if (supplierRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(request.name().trim())) {
            throw new ConflictException("Tên nhà cung cấp đã tồn tại: " + request.name().trim());
        }

        Supplier supplier = new Supplier(
                request.name().trim(),
                request.email() != null ? request.email().trim() : null,
                request.phone() != null ? request.phone().trim() : null,
                request.address() != null ? request.address().trim() : null,
                request.contactName() != null ? request.contactName().trim() : null
        );

        Supplier saved = supplierRepository.save(supplier);
        return purchaseOrderMapper.toSupplierResponse(saved);
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierUpdateRequest request) {
        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với id: " + id));

        String trimmedName = request.name().trim();
        if (!supplier.getName().equalsIgnoreCase(trimmedName)
                && supplierRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(trimmedName)) {
            throw new ConflictException("Tên nhà cung cấp đã tồn tại: " + trimmedName);
        }

        supplier.setName(trimmedName);
        supplier.setEmail(request.email() != null ? request.email().trim() : null);
        supplier.setPhone(request.phone() != null ? request.phone().trim() : null);
        supplier.setAddress(request.address() != null ? request.address().trim() : null);
        supplier.setContactName(request.contactName() != null ? request.contactName().trim() : null);

        Supplier saved = supplierRepository.save(supplier);
        return purchaseOrderMapper.toSupplierResponse(saved);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplier(Long id) {
        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với id: " + id));
        return purchaseOrderMapper.toSupplierResponse(supplier);
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<SupplierResponse>> getSuppliers(String q, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Supplier> supplierPage = supplierRepository.searchSuppliers(
                (q != null && !q.isBlank()) ? q.trim() : null, pageable);

        List<SupplierResponse> dtos = supplierPage.getContent().stream()
                .map(purchaseOrderMapper::toSupplierResponse)
                .toList();

        PageMeta meta = new PageMeta(
                supplierPage.getNumber(),
                supplierPage.getSize(),
                supplierPage.getTotalElements(),
                supplierPage.getTotalPages()
        );

        return ApiResponse.of(dtos, meta);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với id: " + id));

        boolean hasActiveOrders = purchaseOrderRepository.existsBySupplierIdAndStatusNotIn(
                id, List.of(PurchaseOrderStatus.CLOSED, PurchaseOrderStatus.CANCELLED));

        if (hasActiveOrders) {
            throw new ConflictException("Không thể xóa nhà cung cấp vì vẫn còn đơn đặt hàng chưa đóng (CLOSED hoặc CANCELLED)");
        }

        supplier.setDeletedAt(Instant.now());
        supplierRepository.save(supplier);
    }
}
