package com.thupo.managerShop.product.service;

import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.product.dto.ProductCreateRequest;
import com.thupo.managerShop.product.dto.ProductOptionRequest;
import com.thupo.managerShop.product.dto.ProductResponse;
import com.thupo.managerShop.product.dto.ProductUpdateRequest;
import com.thupo.managerShop.product.dto.ProductVariantRequest;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.repository.ProductMediaRepository;
import com.thupo.managerShop.product.repository.ProductOptionRepository;
import com.thupo.managerShop.product.repository.ProductOptionValueRepository;
import com.thupo.managerShop.product.repository.ProductRepository;
import com.thupo.managerShop.product.repository.ProductVariantRepository;
import com.thupo.managerShop.supplychain.repository.InventoryLevelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductOptionRepository productOptionRepository;
    @Mock
    private ProductOptionValueRepository productOptionValueRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductMediaRepository productMediaRepository;
    @Mock
    private InventoryLevelRepository inventoryLevelRepository;

    private ProductMapper productMapper;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productMapper = new ProductMapper(new ObjectMapper());
        productService = new ProductService(
                productRepository,
                productOptionRepository,
                productOptionValueRepository,
                productVariantRepository,
                productMediaRepository,
                inventoryLevelRepository,
                productMapper
        );
    }

    @Test
    @DisplayName("Nghiệp vụ 1: Tạo sản phẩm không option sinh đúng 1 biến thể Default Title")
    void createProductWithoutOptionsGeneratesSingleDefaultTitleVariant() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Sản phẩm cơ bản",
                "<p>Mô tả cơ bản</p>",
                ProductStatus.DRAFT,
                "CAT-1",
                "Danh mục 1",
                "Loại 1",
                "Nhà cung cấp A",
                List.of("tag1", "tag2"),
                "Default product",
                null, // Không có options
                List.of(new ProductVariantRequest(null, null, null, null, null,
                        BigDecimal.valueOf(100000), null, BigDecimal.valueOf(50000),
                        true, null, "SKU-BASIC", "893000001",
                        true, false, true,
                        BigDecimal.valueOf(0.5), null, null, null, null, null, 1)),
                null
        );

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals("Sản phẩm cơ bản", response.title());
        assertEquals(1, response.variants().size());
        assertEquals("Default Title", response.variants().get(0).title());
        assertEquals("SKU-BASIC", response.variants().get(0).sku());
        assertEquals(BigDecimal.valueOf(100000), response.variants().get(0).price());
        assertNull(response.variants().get(0).option1());
    }

    @Test
    @DisplayName("Nghiệp vụ 2: Tạo sản phẩm 2 option (3 size x 2 màu) sinh đúng 6 biến thể")
    void createProductWithTwoOptionsGeneratesSixVariants() {
        List<ProductOptionRequest> options = List.of(
                new ProductOptionRequest("Size", 1, List.of("S", "M", "L")),
                new ProductOptionRequest("Color", 2, List.of("Đen", "Trắng"))
        );

        ProductCreateRequest request = new ProductCreateRequest(
                "Áo polo",
                "<p>Áo polo nam cao cấp</p>",
                ProductStatus.DRAFT,
                "CAT-POLO",
                "Thời trang",
                "Áo polo",
                "Shopify Store",
                List.of("polo", "nam"),
                "Default product",
                options,
                null,
                null
        );

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertNotNull(response);
        assertEquals(2, response.options().size());
        assertEquals(6, response.variants().size());

        List<String> titles = response.variants().stream().map(v -> v.title()).toList();
        assertTrue(titles.contains("S / Đen"));
        assertTrue(titles.contains("S / Trắng"));
        assertTrue(titles.contains("M / Đen"));
        assertTrue(titles.contains("M / Trắng"));
        assertTrue(titles.contains("L / Đen"));
        assertTrue(titles.contains("L / Trắng"));
    }

    @Test
    @DisplayName("Nghiệp vụ 3: Trùng SKU trả lỗi ConflictException (HTTP 409)")
    void duplicateSkuThrowsConflictException() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Sản phẩm test",
                null,
                ProductStatus.DRAFT,
                null, null, null, null, null, null,
                null,
                List.of(new ProductVariantRequest(null, null, null, null, null,
                        BigDecimal.valueOf(50000), null, null, null, null,
                        "SKU-EXISTING", null, null, null, null, null, null, null, null, null, null, 1)),
                null
        );

        when(productVariantRepository.existsBySkuAndDeletedAtIsNull("SKU-EXISTING")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> productService.createProduct(request));
        assertTrue(ex.getMessage().contains("SKU-EXISTING"));
    }

    @Test
    @DisplayName("Nghiệp vụ 4: compare_at_price nhỏ hơn hoặc bằng price trả BadRequestAlertException (HTTP 400)")
    void compareAtPriceLessThanOrEqualToPriceThrowsBadRequest() {
        ProductCreateRequest request = new ProductCreateRequest(
                "Sản phẩm giảm giá lỗi",
                null,
                ProductStatus.DRAFT,
                null, null, null, null, null, null,
                null,
                List.of(new ProductVariantRequest(null, null, null, null, null,
                        BigDecimal.valueOf(100000),
                        BigDecimal.valueOf(90000), // Giá so sánh <= giá bán -> Lỗi!
                        null, null, null,
                        "SKU-INVALID-PRICE", null, null, null, null, null, null, null, null, null, null, 1)),
                null
        );

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class, () -> productService.createProduct(request));
        assertTrue(ex.getMessage().contains("compare_at_price"));
    }

    @Test
    @DisplayName("Nghiệp vụ 5: Xóa mềm xong thì sản phẩm và biến thể có deletedAt; chi tiết trả 404")
    void softDeleteSetsDeletedAtAndDetailReturnsNotFound() {
        Product product = new Product();
        product.setId(10L);
        product.setTitle("Sản phẩm cần xóa");

        ProductVariant variant = new ProductVariant();
        variant.setId(101L);
        variant.setTitle("Default Title");
        variant.setProduct(product);
        product.getVariants().add(variant);

        when(productRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(product));

        productService.deleteProduct(10L);

        assertNotNull(product.getDeletedAt());
        assertNotNull(variant.getDeletedAt());
        verify(productRepository).save(product);

        // Khi tìm chi tiết sau khi xóa mềm
        when(productRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> productService.getProduct(10L));
    }

    @Test
    @DisplayName("Nghiệp vụ bổ sung: Cập nhật option giữ nguyên biến thể cũ, tạo biến thể mới, xóa mềm biến thể thừa")
    void updateOptionsReconcilesVariantsCorrectly() {
        Product product = new Product();
        product.setId(20L);
        product.setTitle("Áo thun thời trang");

        // Biến thể cũ: S / Đen (ID=501), S / Trắng (ID=502)
        ProductVariant v1 = new ProductVariant();
        v1.setId(501L);
        v1.setProduct(product);
        v1.setTitle("S / Đen");
        v1.setOption1("S");
        v1.setOption2("Đen");
        v1.setPrice(BigDecimal.valueOf(150000));
        v1.setSku("AT-S-DEN");

        ProductVariant v2 = new ProductVariant();
        v2.setId(502L);
        v2.setProduct(product);
        v2.setTitle("S / Trắng");
        v2.setOption1("S");
        v2.setOption2("Trắng");
        v2.setPrice(BigDecimal.valueOf(150000));
        v2.setSku("AT-S-TRANG");

        product.getVariants().add(v1);
        product.getVariants().add(v2);

        when(productRepository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Cập nhật: Chỉ còn Color Đen và thêm Color Đỏ (bỏ Trắng)
        List<ProductOptionRequest> newOptions = List.of(
                new ProductOptionRequest("Size", 1, List.of("S")),
                new ProductOptionRequest("Color", 2, List.of("Đen", "Đỏ"))
        );

        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Áo thun thời trang",
                null,
                ProductStatus.DRAFT,
                null, null, null, null, null, null,
                newOptions,
                null,
                null
        );

        ProductResponse response = productService.updateProduct(20L, updateRequest);

        assertNotNull(response);
        // S / Đen phải được GIỮ NGUYÊN
        assertEquals(BigDecimal.valueOf(150000), v1.getPrice());
        assertEquals("AT-S-DEN", v1.getSku());
        assertNull(v1.getDeletedAt());

        // S / Trắng không còn trong tổ hợp -> phải bị XÓA MỀM
        assertNotNull(v2.getDeletedAt());

        // Tổ hợp mới S / Đỏ được tạo
        boolean hasRed = product.getVariants().stream()
                .anyMatch(v -> "S / Đỏ".equals(v.getTitle()) && v.getDeletedAt() == null);
        assertTrue(hasRed);
    }
}
