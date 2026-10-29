package com.thupo.managerShop.product.service;

import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.product.dto.ProductCreateRequest;
import com.thupo.managerShop.product.dto.ProductListResponse;
import com.thupo.managerShop.product.dto.ProductMediaRequest;
import com.thupo.managerShop.product.dto.ProductOptionRequest;
import com.thupo.managerShop.product.dto.ProductResponse;
import com.thupo.managerShop.product.dto.ProductUpdateRequest;
import com.thupo.managerShop.product.dto.ProductVariantRequest;
import com.thupo.managerShop.product.dto.SavedViewsAndColumnsResponse;
import com.thupo.managerShop.product.entity.MediaType;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductMedia;
import com.thupo.managerShop.product.entity.ProductOption;
import com.thupo.managerShop.product.entity.ProductOptionValue;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.repository.ProductMediaRepository;
import com.thupo.managerShop.product.repository.ProductOptionRepository;
import com.thupo.managerShop.product.repository.ProductOptionValueRepository;
import com.thupo.managerShop.product.repository.ProductRepository;
import com.thupo.managerShop.product.repository.ProductVariantRepository;
import com.thupo.managerShop.supplychain.repository.InventoryLevelRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý toàn bộ logic nghiệp vụ của Module Sản phẩm.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductMediaRepository productMediaRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository,
                          ProductOptionRepository productOptionRepository,
                          ProductOptionValueRepository productOptionValueRepository,
                          ProductVariantRepository productVariantRepository,
                          ProductMediaRepository productMediaRepository,
                          InventoryLevelRepository inventoryLevelRepository,
                          ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productOptionRepository = productOptionRepository;
        this.productOptionValueRepository = productOptionValueRepository;
        this.productVariantRepository = productVariantRepository;
        this.productMediaRepository = productMediaRepository;
        this.inventoryLevelRepository = inventoryLevelRepository;
        this.productMapper = productMapper;
    }

    /**
     * Tạo mới sản phẩm cùng Option, Biến thể và Media trong một Transaction.
     */
    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        validateOptionsCount(request.options());
        validatePricingRules(request.variants());
        validateSkusOnCreate(request.variants());

        Product product = new Product();
        product.setTitle(request.title().trim());
        product.setDescription(request.description());
        product.setStatus(request.status() != null ? request.status() : ProductStatus.DRAFT);
        product.setCategoryId(request.categoryId());
        product.setCategoryName(request.categoryName());
        product.setProductType(request.productType());
        product.setVendor(request.vendor());
        product.setTags(productMapper.serializeTags(request.tags()));
        if (request.themeTemplate() != null && !request.themeTemplate().isBlank()) {
            product.setThemeTemplate(request.themeTemplate());
        }

        Product savedProduct = productRepository.save(product);

        // Xử lý Options và Biến thể
        buildOptionsAndVariants(savedProduct, request.options(), request.variants());

        // Xử lý Media
        buildMedia(savedProduct, request.media());

        // Kiểm tra quy tắc sản phẩm ACTIVE
        validateActiveStatusRules(savedProduct);

        int totalInventory = inventoryLevelRepository.sumAvailableByProductId(savedProduct.getId());
        return productMapper.toResponse(savedProduct, totalInventory);
    }

    /**
     * Cập nhật toàn bộ thông tin sản phẩm (Options, Biến thể, Media).
     */
    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));

        validateOptionsCount(request.options());
        validatePricingRules(request.variants());
        validateSkusOnUpdate(id, request.variants());

        product.setTitle(request.title().trim());
        product.setDescription(request.description());
        product.setStatus(request.status() != null ? request.status() : ProductStatus.DRAFT);
        product.setCategoryId(request.categoryId());
        product.setCategoryName(request.categoryName());
        product.setProductType(request.productType());
        product.setVendor(request.vendor());
        product.setTags(productMapper.serializeTags(request.tags()));
        if (request.themeTemplate() != null && !request.themeTemplate().isBlank()) {
            product.setThemeTemplate(request.themeTemplate());
        }

        // Tái tạo / đồng bộ Options và Biến thể
        reconcileOptionsAndVariants(product, request.options(), request.variants());

        // Đồng bộ Media
        buildMedia(product, request.media());

        // Kiểm tra quy tắc sản phẩm ACTIVE
        validateActiveStatusRules(product);

        Product updatedProduct = productRepository.save(product);
        int totalInventory = inventoryLevelRepository.sumAvailableByProductId(updatedProduct.getId());
        return productMapper.toResponse(updatedProduct, totalInventory);
    }

    /**
     * Lấy chi tiết sản phẩm theo ID.
     */
    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));
        int totalInventory = inventoryLevelRepository.sumAvailableByProductId(product.getId());
        return productMapper.toResponse(product, totalInventory);
    }

    /**
     * Danh sách sản phẩm có tìm kiếm, lọc và phân trang.
     */
    @Transactional(readOnly = true)
    public ApiResponse<List<ProductListResponse>> getProducts(String q,
                                                              ProductStatus status,
                                                              String vendor,
                                                              String productType,
                                                              String tag,
                                                              int page,
                                                              int size,
                                                              String sortStr) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(100, Math.max(1, size));

        Sort sort = parseSort(sortStr);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, sort);

        Page<Product> productPage = productRepository.searchProducts(
                (q != null && !q.isBlank()) ? q.trim() : null,
                status,
                (vendor != null && !vendor.isBlank()) ? vendor.trim() : null,
                (productType != null && !productType.isBlank()) ? productType.trim() : null,
                pageable
        );

        List<Product> content = productPage.getContent();
        if (tag != null && !tag.isBlank()) {
            String targetTag = tag.trim().toLowerCase();
            content = content.stream()
                    .filter(p -> productMapper.parseTags(p.getTags()).stream()
                            .anyMatch(t -> t.toLowerCase().contains(targetTag)))
                    .toList();
        }

        List<ProductListResponse> dtos = content.stream()
                .map(p -> productMapper.toListResponse(p, inventoryLevelRepository.sumAvailableByProductId(p.getId())))
                .toList();

        PageMeta meta = new PageMeta(
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages()
        );

        return ApiResponse.of(dtos, meta);
    }

    /**
     * Xóa mềm sản phẩm (set deleted_at cho sản phẩm và các biến thể).
     */
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với id: " + id));

        Instant now = Instant.now();
        product.setDeletedAt(now);

        if (product.getVariants() != null) {
            for (ProductVariant variant : product.getVariants()) {
                if (variant.getDeletedAt() == null) {
                    variant.setDeletedAt(now);
                }
            }
        }

        productRepository.save(product);
    }

    /**
     * Trả về cấu hình cột và view mặc định (SavedViewsAndColumns).
     */
    public SavedViewsAndColumnsResponse getSavedViewsAndColumns() {
        List<String> defaultCols = List.of("PRODUCT", "STATUS", "INVENTORY", "CATEGORY", "TYPE", "VENDOR");
        List<SavedViewsAndColumnsResponse.SavedViewItem> views = List.of(
                new SavedViewsAndColumnsResponse.SavedViewItem("gid://shopify/SavedView/1", "All", "Tất cả", 1, defaultCols),
                new SavedViewsAndColumnsResponse.SavedViewItem("gid://shopify/SavedView/2", "Active", "Đang bán", 2, defaultCols),
                new SavedViewsAndColumnsResponse.SavedViewItem("gid://shopify/SavedView/3", "Draft", "Bản nháp", 3, defaultCols),
                new SavedViewsAndColumnsResponse.SavedViewItem("gid://shopify/SavedView/4", "Archived", "Đã lưu trữ", 4, defaultCols)
        );
        return new SavedViewsAndColumnsResponse(views, defaultCols);
    }

    // =========================================================================
    // Private Helper Methods
    // =========================================================================

    private void validateOptionsCount(List<ProductOptionRequest> options) {
        if (options != null && options.size() > 3) {
            throw new BadRequestAlertException("Mỗi sản phẩm chỉ được có tối đa 3 thuộc tính (options)");
        }
    }

    private void validatePricingRules(List<ProductVariantRequest> variants) {
        if (variants == null) {
            return;
        }
        for (ProductVariantRequest v : variants) {
            if (v.price() != null && v.price().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestAlertException("Giá bán (price) không được nhỏ hơn 0");
            }
            if (v.compareAtPrice() != null) {
                BigDecimal currentPrice = v.price() != null ? v.price() : BigDecimal.ZERO;
                if (v.compareAtPrice().compareTo(currentPrice) <= 0) {
                    throw new BadRequestAlertException("Giá so sánh (compare_at_price) phải lớn hơn giá bán (price)");
                }
            }
        }
    }

    private void validateSkusOnCreate(List<ProductVariantRequest> variants) {
        if (variants == null) {
            return;
        }
        Set<String> skusInRequest = new HashSet<>();
        for (ProductVariantRequest v : variants) {
            if (v.sku() != null && !v.sku().isBlank()) {
                String sku = v.sku().trim();
                if (!skusInRequest.add(sku.toLowerCase())) {
                    throw new ConflictException("Mã SKU '" + sku + "' bị trùng lặp trong yêu cầu");
                }
                if (productVariantRepository.existsBySkuAndDeletedAtIsNull(sku)) {
                    throw new ConflictException("Mã SKU '" + sku + "' đã tồn tại trong hệ thống");
                }
            }
        }
    }

    private void validateSkusOnUpdate(Long productId, List<ProductVariantRequest> variants) {
        if (variants == null) {
            return;
        }
        Set<String> skusInRequest = new HashSet<>();
        for (ProductVariantRequest v : variants) {
            if (v.sku() != null && !v.sku().isBlank()) {
                String sku = v.sku().trim();
                if (!skusInRequest.add(sku.toLowerCase())) {
                    throw new ConflictException("Mã SKU '" + sku + "' bị trùng lặp trong yêu cầu");
                }
                var existing = productVariantRepository.findBySkuAndDeletedAtIsNull(sku);
                if (existing.isPresent() && !existing.get().getProduct().getId().equals(productId)) {
                    throw new ConflictException("Mã SKU '" + sku + "' đã tồn tại trong hệ thống");
                }
            }
        }
    }

    private void validateActiveStatusRules(Product product) {
        if (product.getStatus() == ProductStatus.ACTIVE) {
            boolean hasPricedVariant = product.getVariants().stream()
                    .filter(v -> v.getDeletedAt() == null)
                    .anyMatch(v -> v.getPrice() != null && v.getPrice().compareTo(BigDecimal.ZERO) >= 0);

            if (!hasPricedVariant) {
                throw new BadRequestAlertException("Sản phẩm ở trạng thái ACTIVE phải có ít nhất một biến thể có giá");
            }
        }
    }

    private void buildOptionsAndVariants(Product product,
                                         List<ProductOptionRequest> optionRequests,
                                         List<ProductVariantRequest> variantRequests) {
        product.getOptions().clear();
        product.getVariants().clear();

        if (optionRequests == null || optionRequests.isEmpty()) {
            // Không có option: tự tạo đúng 1 biến thể 'Default Title'
            ProductVariant defaultVariant = new ProductVariant();
            defaultVariant.setProduct(product);
            defaultVariant.setTitle("Default Title");
            defaultVariant.setPosition(1);

            if (variantRequests != null && !variantRequests.isEmpty()) {
                applyVariantData(defaultVariant, variantRequests.get(0));
            } else {
                defaultVariant.setPrice(BigDecimal.ZERO);
            }
            product.getVariants().add(defaultVariant);
            return;
        }

        // Tạo options và option_values
        List<ProductOption> options = new ArrayList<>();
        int optPos = 1;
        for (ProductOptionRequest optReq : optionRequests) {
            ProductOption opt = new ProductOption(product, optReq.name().trim(), optReq.position() != null ? optReq.position() : optPos++);
            List<ProductOptionValue> vals = new ArrayList<>();
            if (optReq.values() != null) {
                int valPos = 1;
                for (String v : optReq.values()) {
                    if (v != null && !v.isBlank()) {
                        vals.add(new ProductOptionValue(opt, v.trim(), valPos++));
                    }
                }
            }
            opt.setValues(vals);
            options.add(opt);
        }
        product.getOptions().addAll(options);

        // Sinh tổ hợp biến thể Descartes
        List<VariantCombination> combinations = generateCartesianCombinations(options);

        // Ánh xạ dữ liệu biến thể từ client (nếu có)
        Map<String, ProductVariantRequest> requestMap = buildVariantRequestMap(variantRequests);

        int varPos = 1;
        for (VariantCombination comb : combinations) {
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setTitle(comb.title);
            variant.setOption1(comb.opt1);
            variant.setOption2(comb.opt2);
            variant.setOption3(comb.opt3);
            variant.setPosition(varPos++);

            ProductVariantRequest matchedReq = requestMap.get(comb.key());
            if (matchedReq != null) {
                applyVariantData(variant, matchedReq);
            } else {
                variant.setPrice(BigDecimal.ZERO);
            }
            product.getVariants().add(variant);
        }
    }

    private void reconcileOptionsAndVariants(Product product,
                                             List<ProductOptionRequest> optionRequests,
                                             List<ProductVariantRequest> variantRequests) {
        if (optionRequests == null || optionRequests.isEmpty()) {
            // Chuyển sang không có option: giữ 1 biến thể Default Title, xóa mềm các biến thể khác
            product.getOptions().clear();

            ProductVariant defaultVariant = null;
            Instant now = Instant.now();
            for (ProductVariant v : product.getVariants()) {
                if (v.getDeletedAt() == null) {
                    if (defaultVariant == null) {
                        defaultVariant = v;
                        defaultVariant.setTitle("Default Title");
                        defaultVariant.setOption1(null);
                        defaultVariant.setOption2(null);
                        defaultVariant.setOption3(null);
                    } else {
                        v.setDeletedAt(now);
                    }
                }
            }
            if (defaultVariant == null) {
                defaultVariant = new ProductVariant();
                defaultVariant.setProduct(product);
                defaultVariant.setTitle("Default Title");
                defaultVariant.setPosition(1);
                product.getVariants().add(defaultVariant);
            }
            if (variantRequests != null && !variantRequests.isEmpty()) {
                applyVariantData(defaultVariant, variantRequests.get(0));
            }
            return;
        }

        // Cập nhật Options
        product.getOptions().clear();
        int optPos = 1;
        List<ProductOption> newOptions = new ArrayList<>();
        for (ProductOptionRequest optReq : optionRequests) {
            ProductOption opt = new ProductOption(product, optReq.name().trim(), optReq.position() != null ? optReq.position() : optPos++);
            List<ProductOptionValue> vals = new ArrayList<>();
            if (optReq.values() != null) {
                int valPos = 1;
                for (String v : optReq.values()) {
                    if (v != null && !v.isBlank()) {
                        vals.add(new ProductOptionValue(opt, v.trim(), valPos++));
                    }
                }
            }
            opt.setValues(vals);
            newOptions.add(opt);
        }
        product.getOptions().addAll(newOptions);

        // Sinh danh sách tổ hợp mới
        List<VariantCombination> newCombs = generateCartesianCombinations(newOptions);
        Set<String> activeNewKeys = new HashSet<>();
        for (VariantCombination c : newCombs) {
            activeNewKeys.add(c.key());
        }

        // Tìm các biến thể cũ đang active
        Map<String, ProductVariant> existingActiveVariants = new HashMap<>();
        Instant now = Instant.now();
        for (ProductVariant v : product.getVariants()) {
            if (v.getDeletedAt() == null) {
                String key = makeCombinationKey(v.getOption1(), v.getOption2(), v.getOption3());
                if (activeNewKeys.contains(key)) {
                    existingActiveVariants.put(key, v);
                } else {
                    // Tổ hợp không còn -> xóa mềm
                    v.setDeletedAt(now);
                }
            }
        }

        // Ánh xạ request map từ client
        Map<String, ProductVariantRequest> requestMap = buildVariantRequestMap(variantRequests);

        int pos = 1;
        for (VariantCombination comb : newCombs) {
            ProductVariant variant = existingActiveVariants.get(comb.key());
            if (variant != null) {
                // Biến thể đã tồn tại: giữ nguyên id, chỉ cập nhật thông tin nếu client gửi
                variant.setPosition(pos++);
                ProductVariantRequest req = requestMap.get(comb.key());
                if (req != null) {
                    applyVariantData(variant, req);
                }
            } else {
                // Tổ hợp mới: tạo mới
                variant = new ProductVariant();
                variant.setProduct(product);
                variant.setTitle(comb.title);
                variant.setOption1(comb.opt1);
                variant.setOption2(comb.opt2);
                variant.setOption3(comb.opt3);
                variant.setPosition(pos++);

                ProductVariantRequest req = requestMap.get(comb.key());
                if (req != null) {
                    applyVariantData(variant, req);
                } else {
                    variant.setPrice(BigDecimal.ZERO);
                }
                product.getVariants().add(variant);
            }
        }
    }

    private void buildMedia(Product product, List<ProductMediaRequest> mediaRequests) {
        product.getMedia().clear();
        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }
        int pos = 1;
        for (ProductMediaRequest mReq : mediaRequests) {
            if (mReq.url() != null && !mReq.url().isBlank()) {
                ProductMedia media = new ProductMedia();
                media.setProduct(product);
                media.setUrl(mReq.url().trim());
                media.setMediaType(mReq.mediaType() != null ? mReq.mediaType() : MediaType.IMAGE);
                media.setAltText(mReq.altText());
                media.setPosition(mReq.position() != null ? mReq.position() : pos++);
                product.getMedia().add(media);
            }
        }
    }

    private void applyVariantData(ProductVariant variant, ProductVariantRequest req) {
        if (req.price() != null) {
            variant.setPrice(req.price());
        }
        variant.setCompareAtPrice(req.compareAtPrice());
        variant.setCostPerItem(req.costPerItem());
        if (req.chargeTax() != null) {
            variant.setChargeTax(req.chargeTax());
        }
        variant.setUnitPriceMeasure(req.unitPriceMeasure());
        if (req.sku() != null && !req.sku().isBlank()) {
            variant.setSku(req.sku().trim());
        }
        variant.setBarcode(req.barcode());
        if (req.inventoryTracked() != null) {
            variant.setInventoryTracked(req.inventoryTracked());
        }
        if (req.sellWhenOutOfStock() != null) {
            variant.setSellWhenOutOfStock(req.sellWhenOutOfStock());
        }
        if (req.isPhysical() != null) {
            variant.setPhysical(req.isPhysical());
        }
        variant.setWeightKg(req.weightKg());
        variant.setLengthCm(req.lengthCm());
        variant.setWidthCm(req.widthCm());
        variant.setHeightCm(req.heightCm());
        variant.setCountryOfOrigin(req.countryOfOrigin());
        variant.setHsCode(req.hsCode());
    }

    private List<VariantCombination> generateCartesianCombinations(List<ProductOption> options) {
        if (options == null || options.isEmpty()) {
            return List.of(new VariantCombination(null, null, null, "Default Title"));
        }

        List<List<String>> optionValuesList = new ArrayList<>();
        for (ProductOption opt : options) {
            List<String> vals = opt.getValues().stream().map(ProductOptionValue::getValue).toList();
            if (vals.isEmpty()) {
                vals = List.of(opt.getName());
            }
            optionValuesList.add(vals);
        }

        List<VariantCombination> results = new ArrayList<>();
        generateCombinationsRecursive(optionValuesList, 0, new String[3], results);
        return results;
    }

    private void generateCombinationsRecursive(List<List<String>> optionValuesList,
                                               int depth,
                                               String[] current,
                                               List<VariantCombination> results) {
        if (depth == optionValuesList.size()) {
            String o1 = current[0];
            String o2 = optionValuesList.size() > 1 ? current[1] : null;
            String o3 = optionValuesList.size() > 2 ? current[2] : null;

            StringBuilder titleBuilder = new StringBuilder();
            if (o1 != null) titleBuilder.append(o1);
            if (o2 != null) titleBuilder.append(" / ").append(o2);
            if (o3 != null) titleBuilder.append(" / ").append(o3);

            results.add(new VariantCombination(o1, o2, o3, titleBuilder.toString()));
            return;
        }

        for (String val : optionValuesList.get(depth)) {
            current[depth] = val;
            generateCombinationsRecursive(optionValuesList, depth + 1, current, results);
        }
    }

    private Map<String, ProductVariantRequest> buildVariantRequestMap(List<ProductVariantRequest> variantRequests) {
        Map<String, ProductVariantRequest> map = new HashMap<>();
        if (variantRequests == null) {
            return map;
        }
        for (ProductVariantRequest req : variantRequests) {
            String key = makeCombinationKey(req.option1(), req.option2(), req.option3());
            map.put(key, req);
            if (req.title() != null && !req.title().isBlank()) {
                map.put(req.title().trim().toLowerCase(), req);
            }
        }
        return map;
    }

    private String makeCombinationKey(String o1, String o2, String o3) {
        return safeStr(o1) + "#" + safeStr(o2) + "#" + safeStr(o3);
    }

    private String safeStr(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private Sort parseSort(String sortStr) {
        if (sortStr == null || sortStr.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sortStr.split(",");
        String field = parts[0].trim();
        Sort.Direction dir = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return switch (field) {
            case "title" -> Sort.by(dir, "title");
            case "createdAt", "created_at" -> Sort.by(dir, "createdAt");
            case "updatedAt", "updated_at" -> Sort.by(dir, "updatedAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }

    private record VariantCombination(String opt1, String opt2, String opt3, String title) {
        String key() {
            return (opt1 == null ? "" : opt1.trim().toLowerCase()) + "#"
                 + (opt2 == null ? "" : opt2.trim().toLowerCase()) + "#"
                 + (opt3 == null ? "" : opt3.trim().toLowerCase());
        }
    }
}
