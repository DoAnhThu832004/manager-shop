package com.thupo.managerShop.product.service;

import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.product.dto.CollectionCreateRequest;
import com.thupo.managerShop.product.dto.CollectionListResponse;
import com.thupo.managerShop.product.dto.CollectionResponse;
import com.thupo.managerShop.product.dto.CollectionRuleRequest;
import com.thupo.managerShop.product.dto.CollectionUpdateRequest;
import com.thupo.managerShop.product.dto.ProductListResponse;
import com.thupo.managerShop.product.entity.Collection;
import com.thupo.managerShop.product.entity.CollectionProduct;
import com.thupo.managerShop.product.entity.CollectionProductId;
import com.thupo.managerShop.product.entity.CollectionRule;
import com.thupo.managerShop.product.entity.CollectionType;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.repository.CollectionProductRepository;
import com.thupo.managerShop.product.repository.CollectionRepository;
import com.thupo.managerShop.product.repository.CollectionRuleRepository;
import com.thupo.managerShop.product.repository.ProductRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý toàn bộ logic nghiệp vụ của Module Bộ sưu tập (Collection).
 */
@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionRuleRepository collectionRuleRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final ProductRepository productRepository;
    private final CollectionMapper collectionMapper;
    private final ProductMapper productMapper;

    public CollectionService(CollectionRepository collectionRepository,
                             CollectionRuleRepository collectionRuleRepository,
                             CollectionProductRepository collectionProductRepository,
                             ProductRepository productRepository,
                             CollectionMapper collectionMapper,
                             ProductMapper productMapper) {
        this.collectionRepository = collectionRepository;
        this.collectionRuleRepository = collectionRuleRepository;
        this.collectionProductRepository = collectionProductRepository;
        this.productRepository = productRepository;
        this.collectionMapper = collectionMapper;
        this.productMapper = productMapper;
    }

    /**
     * Tạo mới bộ sưu tập (MANUAL hoặc AUTOMATED).
     */
    @Transactional
    public CollectionResponse createCollection(CollectionCreateRequest request) {
        validateCollectionTypeRulesOnCreate(request);

        Collection collection = new Collection();
        collection.setTitle(request.title().trim());
        collection.setDescription(request.description());
        collection.setImageUrl(request.imageUrl());
        collection.setCollectionType(request.collectionType());
        collection.setRuleAppliesDisjunctive(Boolean.TRUE.equals(request.ruleAppliesDisjunctive()));

        Collection savedCollection = collectionRepository.save(collection);

        if (savedCollection.getCollectionType() == CollectionType.MANUAL) {
            if (request.productIds() != null && !request.productIds().isEmpty()) {
                addInitialProductsToManualCollection(savedCollection, request.productIds());
            }
        } else {
            // AUTOMATED
            addRulesToAutomatedCollection(savedCollection, request.rules());
        }

        long productsCount = calculateProductsCount(savedCollection);
        return collectionMapper.toResponse(savedCollection, productsCount, null, null);
    }

    /**
     * Cập nhật thông tin bộ sưu tập. Hỗ trợ chuyển đổi loại MANUAL <-> AUTOMATED khi có confirmSwitch=true.
     */
    @Transactional
    public CollectionResponse updateCollection(Long id, CollectionUpdateRequest request) {
        Collection collection = collectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ sưu tập với id: " + id));

        CollectionType targetType = request.collectionType() != null ? request.collectionType() : collection.getCollectionType();

        // Kiểm tra chuyển đổi loại MANUAL <-> AUTOMATED
        if (targetType != collection.getCollectionType()) {
            if (!Boolean.TRUE.equals(request.confirmSwitch())) {
                throw new ConflictException("Chuyển đổi loại bộ sưu tập sẽ xóa dữ liệu cấu hình cũ, vui lòng xác nhận bằng confirmSwitch=true");
            }
            if (targetType == CollectionType.MANUAL) {
                // Chuyển sang MANUAL: xóa toàn bộ rules cũ
                collectionRuleRepository.deleteByCollectionId(collection.getId());
                collection.getRules().clear();
            } else {
                // Chuyển sang AUTOMATED: xóa toàn bộ sản phẩm thủ công cũ
                collectionProductRepository.deleteByCollectionId(collection.getId());
                collection.getCollectionProducts().clear();
            }
            collection.setCollectionType(targetType);
        }

        collection.setTitle(request.title().trim());
        collection.setDescription(request.description());
        collection.setImageUrl(request.imageUrl());
        if (request.ruleAppliesDisjunctive() != null) {
            collection.setRuleAppliesDisjunctive(request.ruleAppliesDisjunctive());
        }

        // Kiểm tra và cập nhật rules cho AUTOMATED
        if (collection.getCollectionType() == CollectionType.AUTOMATED) {
            if (request.rules() == null || request.rules().isEmpty()) {
                throw new BadRequestAlertException("Bộ sưu tập tự động (AUTOMATED) phải có ít nhất một quy tắc điều kiện (rules)");
            }
            collectionRuleRepository.deleteByCollectionId(collection.getId());
            collection.getRules().clear();
            addRulesToAutomatedCollection(collection, request.rules());
        } else {
            if (request.rules() != null && !request.rules().isEmpty()) {
                throw new BadRequestAlertException("Bộ sưu tập thủ công (MANUAL) không được có quy tắc điều kiện (rules)");
            }
        }

        Collection updatedCollection = collectionRepository.save(collection);
        long productsCount = calculateProductsCount(updatedCollection);
        return collectionMapper.toResponse(updatedCollection, productsCount, null, null);
    }

    /**
     * Lấy chi tiết bộ sưu tập kèm danh sách sản phẩm phân trang.
     */
    @Transactional(readOnly = true)
    public CollectionResponse getCollection(Long id, int productPage, int productSize) {
        Collection collection = collectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ sưu tập với id: " + id));

        int pPage = Math.max(0, productPage);
        int pSize = Math.min(100, Math.max(1, productSize));
        Pageable pageable = PageRequest.of(pPage, pSize);

        Page<Product> productPageResult;
        if (collection.getCollectionType() == CollectionType.MANUAL) {
            productPageResult = collectionProductRepository.findActiveProductsByCollectionId(id, pageable);
        } else {
            Specification<Product> spec = CollectionProductSpecification.buildSpecification(
                    collection.getRules(), collection.isRuleAppliesDisjunctive());
            productPageResult = productRepository.findAll(spec, pageable);
        }

        List<ProductListResponse> productDtos = productPageResult.getContent().stream()
                .map(p -> productMapper.toListResponse(p, 0))
                .toList();

        PageMeta meta = new PageMeta(
                productPageResult.getNumber(),
                productPageResult.getSize(),
                productPageResult.getTotalElements(),
                productPageResult.getTotalPages()
        );

        long totalCount = productPageResult.getTotalElements();
        return collectionMapper.toResponse(collection, totalCount, productDtos, meta);
    }

    /**
     * Lấy danh sách bộ sưu tập có tìm kiếm theo title, lọc theo loại và phân trang.
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<CollectionListResponse>> getCollections(String q,
                                                                    CollectionType type,
                                                                    int page,
                                                                    int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Collection> collectionPage = collectionRepository.searchCollections(
                (q != null && !q.isBlank()) ? q.trim() : null,
                type,
                pageable
        );

        List<CollectionListResponse> dtos = collectionPage.getContent().stream()
                .map(c -> {
                    long count = calculateProductsCount(c);
                    return collectionMapper.toListResponse(c, count);
                })
                .toList();

        PageMeta meta = new PageMeta(
                collectionPage.getNumber(),
                collectionPage.getSize(),
                collectionPage.getTotalElements(),
                collectionPage.getTotalPages()
        );

        return ApiResponse.of(dtos, meta);
    }

    /**
     * Xóa mềm bộ sưu tập (set deleted_at).
     */
    @Transactional
    public void deleteCollection(Long id) {
        Collection collection = collectionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ sưu tập với id: " + id));

        collection.setDeletedAt(Instant.now());
        collectionRepository.save(collection);
    }

    /**
     * Thêm sản phẩm vào bộ sưu tập thủ công (MANUAL).
     */
    @Transactional
    public void addProductToCollection(Long collectionId, Long productId, Integer position) {
        Collection collection = collectionRepository.findByIdAndDeletedAtIsNull(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ sưu tập với id: " + collectionId));

        if (collection.getCollectionType() != CollectionType.MANUAL) {
            throw new BadRequestAlertException("Không thể thêm sản phẩm thủ công vào bộ sưu tập tự động (AUTOMATED)");
        }

        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + productId));

        CollectionProductId colProdId = new CollectionProductId(collectionId, productId);
        if (collectionProductRepository.existsById(colProdId)) {
            throw new ConflictException("Sản phẩm đã tồn tại trong bộ sưu tập");
        }

        int pos = position != null ? position : 1;
        if (position == null) {
            Integer maxPos = collectionProductRepository.findMaxPositionByCollectionId(collectionId);
            pos = (maxPos != null) ? maxPos + 1 : 1;
        }

        CollectionProduct cp = new CollectionProduct(collection, product, pos);
        collectionProductRepository.save(cp);
    }

    /**
     * Xóa sản phẩm khỏi bộ sưu tập thủ công (MANUAL).
     */
    @Transactional
    public void removeProductFromCollection(Long collectionId, Long productId) {
        Collection collection = collectionRepository.findByIdAndDeletedAtIsNull(collectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ sưu tập với id: " + collectionId));

        if (collection.getCollectionType() != CollectionType.MANUAL) {
            throw new BadRequestAlertException("Không thể xóa sản phẩm thủ công khỏi bộ sưu tập tự động (AUTOMATED)");
        }

        CollectionProductId colProdId = new CollectionProductId(collectionId, productId);
        collectionProductRepository.deleteById(colProdId);
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    private void validateCollectionTypeRulesOnCreate(CollectionCreateRequest request) {
        if (request.collectionType() == CollectionType.MANUAL) {
            if (request.rules() != null && !request.rules().isEmpty()) {
                throw new BadRequestAlertException("Bộ sưu tập thủ công (MANUAL) không được có quy tắc điều kiện (rules)");
            }
        } else if (request.collectionType() == CollectionType.AUTOMATED) {
            if (request.rules() == null || request.rules().isEmpty()) {
                throw new BadRequestAlertException("Bộ sưu tập tự động (AUTOMATED) phải có ít nhất một quy tắc điều kiện (rules)");
            }
            if (request.productIds() != null && !request.productIds().isEmpty()) {
                throw new BadRequestAlertException("Bộ sưu tập tự động (AUTOMATED) không nhận danh sách sản phẩm thủ công");
            }
        }
    }

    private void addInitialProductsToManualCollection(Collection collection, List<Long> productIds) {
        Set<Long> uniqueIds = new HashSet<>();
        int pos = 1;
        for (Long pid : productIds) {
            if (pid != null && uniqueIds.add(pid)) {
                var prodOpt = productRepository.findByIdAndDeletedAtIsNull(pid);
                if (prodOpt.isPresent()) {
                    CollectionProduct cp = new CollectionProduct(collection, prodOpt.get(), pos++);
                    collectionProductRepository.save(cp);
                }
            }
        }
    }

    private void addRulesToAutomatedCollection(Collection collection, List<CollectionRuleRequest> ruleRequests) {
        if (ruleRequests == null) return;
        List<CollectionRule> rules = new ArrayList<>();
        for (CollectionRuleRequest rReq : ruleRequests) {
            boolean isExcl = Boolean.TRUE.equals(rReq.isExclusion());
            CollectionRule rule = new CollectionRule(collection, rReq.field(), rReq.operator(), rReq.value().trim(), isExcl);
            rules.add(rule);
        }
        collectionRuleRepository.saveAll(rules);
        collection.getRules().addAll(rules);
    }

    public long calculateProductsCount(Collection collection) {
        if (collection.getCollectionType() == CollectionType.MANUAL) {
            return collectionProductRepository.countActiveProductsByCollectionId(collection.getId());
        } else {
            Specification<Product> spec = CollectionProductSpecification.buildSpecification(
                    collection.getRules(), collection.isRuleAppliesDisjunctive());
            return productRepository.count(spec);
        }
    }
}
