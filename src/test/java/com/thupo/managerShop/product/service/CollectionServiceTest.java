package com.thupo.managerShop.product.service;

import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.product.dto.CollectionCreateRequest;
import com.thupo.managerShop.product.dto.CollectionResponse;
import com.thupo.managerShop.product.dto.CollectionRuleRequest;
import com.thupo.managerShop.product.dto.CollectionUpdateRequest;
import com.thupo.managerShop.product.entity.Collection;
import com.thupo.managerShop.product.entity.CollectionProductId;
import com.thupo.managerShop.product.entity.CollectionType;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.RuleField;
import com.thupo.managerShop.product.entity.RuleOperator;
import com.thupo.managerShop.product.repository.CollectionProductRepository;
import com.thupo.managerShop.product.repository.CollectionRepository;
import com.thupo.managerShop.product.repository.CollectionRuleRepository;
import com.thupo.managerShop.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private CollectionRuleRepository collectionRuleRepository;
    @Mock
    private CollectionProductRepository collectionProductRepository;
    @Mock
    private ProductRepository productRepository;

    private CollectionMapper collectionMapper;
    private ProductMapper productMapper;
    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        collectionMapper = new CollectionMapper();
        productMapper = new ProductMapper(new ObjectMapper());
        collectionService = new CollectionService(
                collectionRepository,
                collectionRuleRepository,
                collectionProductRepository,
                productRepository,
                collectionMapper,
                productMapper
        );
    }

    @Test
    @DisplayName("Nghiệp vụ 1: Tạo bộ sưu tập MANUAL có kèm rule trả lỗi 400 BadRequestAlertException")
    void createManualCollectionWithRulesThrowsBadRequest() {
        CollectionCreateRequest request = new CollectionCreateRequest(
                "Bộ sưu tập thủ công lỗi",
                "Mô tả",
                null,
                CollectionType.MANUAL,
                false,
                List.of(new CollectionRuleRequest(RuleField.VENDOR, RuleOperator.EQUALS, "Nike", false)),
                null
        );

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> collectionService.createCollection(request));
        assertTrue(ex.getMessage().contains("MANUAL"));
    }

    @Test
    @DisplayName("Nghiệp vụ 2: Tạo bộ sưu tập AUTOMATED không có rule trả lỗi 400 BadRequestAlertException")
    void createAutomatedCollectionWithoutRulesThrowsBadRequest() {
        CollectionCreateRequest request = new CollectionCreateRequest(
                "Bộ sưu tập tự động lỗi",
                "Mô tả",
                null,
                CollectionType.AUTOMATED,
                false,
                null, // Không có rules
                null
        );

        BadRequestAlertException ex = assertThrows(BadRequestAlertException.class,
                () -> collectionService.createCollection(request));
        assertTrue(ex.getMessage().contains("AUTOMATED"));
    }

    @Test
    @DisplayName("Nghiệp vụ 3: Thêm trùng sản phẩm vào bộ sưu tập MANUAL trả lỗi 409 ConflictException")
    void addDuplicateProductToManualCollectionThrowsConflict() {
        Long colId = 10L;
        Long prodId = 101L;

        Collection col = new Collection();
        col.setId(colId);
        col.setTitle("Bộ sưu tập hè");
        col.setCollectionType(CollectionType.MANUAL);

        Product prod = new Product();
        prod.setId(prodId);
        prod.setStatus(ProductStatus.ACTIVE);

        when(collectionRepository.findByIdAndDeletedAtIsNull(colId)).thenReturn(Optional.of(col));
        when(productRepository.findByIdAndDeletedAtIsNull(prodId)).thenReturn(Optional.of(prod));
        when(collectionProductRepository.existsById(new CollectionProductId(colId, prodId))).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> collectionService.addProductToCollection(colId, prodId, 1));
        assertTrue(ex.getMessage().contains("đã tồn tại"));
    }

    @Test
    @DisplayName("Nghiệp vụ 4: Chuyển đổi loại MANUAL <-> AUTOMATED không có confirmSwitch=true trả lỗi 409 Conflict")
    void switchCollectionTypeWithoutConfirmSwitchThrowsConflict() {
        Long colId = 15L;
        Collection col = new Collection();
        col.setId(colId);
        col.setTitle("Bộ sưu tập thử nghiệm");
        col.setCollectionType(CollectionType.MANUAL);

        when(collectionRepository.findByIdAndDeletedAtIsNull(colId)).thenReturn(Optional.of(col));

        CollectionUpdateRequest updateRequest = new CollectionUpdateRequest(
                "Bộ sưu tập thử nghiệm",
                null,
                null,
                CollectionType.AUTOMATED, // Đổi sang AUTOMATED
                false,
                List.of(new CollectionRuleRequest(RuleField.TITLE, RuleOperator.CONTAINS, "Giày", false)),
                false // confirmSwitch = false!
        );

        ConflictException ex = assertThrows(ConflictException.class,
                () -> collectionService.updateCollection(colId, updateRequest));
        assertTrue(ex.getMessage().contains("confirmSwitch=true"));
    }

    @Test
    @DisplayName("Nghiệp vụ 5: Xóa mềm bộ sưu tập thành công và chi tiết sau đó trả 404")
    void deleteCollectionSoftDeletesAndGetThrowsNotFound() {
        Long colId = 20L;
        Collection col = new Collection();
        col.setId(colId);
        col.setTitle("Bộ sưu tập cần xóa");

        when(collectionRepository.findByIdAndDeletedAtIsNull(colId)).thenReturn(Optional.of(col));

        collectionService.deleteCollection(colId);

        assertNotNull(col.getDeletedAt());
        verify(collectionRepository).save(col);

        when(collectionRepository.findByIdAndDeletedAtIsNull(colId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> collectionService.getCollection(colId, 0, 20));
    }
}
