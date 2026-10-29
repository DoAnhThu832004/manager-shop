package com.thupo.managerShop.supplychain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.repository.BranchRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.repository.ProductVariantRepository;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderCreateRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderItemRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiveItemRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiveRequest;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderResponse;
import com.thupo.managerShop.supplychain.entity.MovementType;
import com.thupo.managerShop.supplychain.entity.PurchaseOrder;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderItem;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderReceipt;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderStatus;
import com.thupo.managerShop.supplychain.entity.Supplier;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderItemRepository;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderReceiptRepository;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderRepository;
import com.thupo.managerShop.supplychain.repository.SupplierRepository;
import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock
    private PurchaseOrderReceiptRepository purchaseOrderReceiptRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InventoryService inventoryService;

    private PurchaseOrderMapper purchaseOrderMapper;
    private PurchaseOrderService purchaseOrderService;
    private SupplierService supplierService;

    private Supplier testSupplier;
    private Branch testBranch;
    private Product testProduct;
    private ProductVariant variant1;
    private ProductVariant variant2;
    private User testUser;

    @BeforeEach
    void setUp() {
        purchaseOrderMapper = new PurchaseOrderMapper(new ObjectMapper());
        purchaseOrderService = new PurchaseOrderService(
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                purchaseOrderReceiptRepository,
                supplierRepository,
                branchRepository,
                productVariantRepository,
                userRepository,
                inventoryService,
                purchaseOrderMapper
        );

        supplierService = new SupplierService(supplierRepository, purchaseOrderRepository, purchaseOrderMapper);

        testSupplier = new Supplier("Công ty May Mặc Hà Nội", "contact@garment.vn", "0243123456", "Hà Nội", "Nguyễn Văn A");
        testSupplier.setId(1L);

        testBranch = new Branch();
        testBranch.setId(1L);
        testBranch.setName("Kho Tổng Hà Nội");
        testBranch.setCode("KHO-HN");

        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setTitle("Áo thun cotton");
        testProduct.setStatus(ProductStatus.ACTIVE);

        variant1 = new ProductVariant();
        variant1.setId(101L);
        variant1.setProduct(testProduct);
        variant1.setTitle("Đen / L");
        variant1.setSku("SKU-BLK-L");
        variant1.setPrice(BigDecimal.valueOf(200000));
        variant1.setInventoryTracked(true);

        variant2 = new ProductVariant();
        variant2.setId(102L);
        variant2.setProduct(testProduct);
        variant2.setTitle("Trắng / M");
        variant2.setSku("SKU-WHT-M");
        variant2.setPrice(BigDecimal.valueOf(180000));
        variant2.setInventoryTracked(true);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("admin@shop.com");
    }

    @Test
    @DisplayName("Nghiệp vụ 1: Nhận một phần rồi nhận nốt: trạng thái đi PARTIALLY_RECEIVED -> RECEIVED, tồn kho tăng đúng số nhận")
    void receivePurchaseOrder_partialThenFull_transitionsFromPartiallyReceivedToReceived() {
        PurchaseOrder po = new PurchaseOrder("PO-000001", testSupplier, testBranch);
        po.setId(1L);
        po.setStatus(PurchaseOrderStatus.ORDERED);

        PurchaseOrderItem item1 = new PurchaseOrderItem(po, variant1, 10, BigDecimal.valueOf(100000));
        item1.setId(11L);
        PurchaseOrderItem item2 = new PurchaseOrderItem(po, variant2, 20, BigDecimal.valueOf(90000));
        item2.setId(12L);
        po.getItems().addAll(List.of(item1, item2));

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderReceiptRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        // Lần 1: Nhận một phần (item1 nhận 6, item2 nhận 8)
        PurchaseOrderReceiveRequest reqPart = new PurchaseOrderReceiveRequest(
                "IDEM-PART-1",
                "Nhận lần 1",
                List.of(
                        new PurchaseOrderReceiveItemRequest(11L, 6),
                        new PurchaseOrderReceiveItemRequest(12L, 8)
                )
        );

        PurchaseOrderResponse resp1 = purchaseOrderService.receivePurchaseOrder(1L, reqPart, 1L);
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, resp1.status());
        assertEquals(6, item1.getQuantityReceived());
        assertEquals(8, item2.getQuantityReceived());

        verify(inventoryService).receiveStock(eq(101L), eq(1L), eq(6), eq(MovementType.PO_RECEIVE), any(), eq("PURCHASE_ORDER"), eq("PO-000001"), eq(1L));
        verify(inventoryService).receiveStock(eq(102L), eq(1L), eq(8), eq(MovementType.PO_RECEIVE), any(), eq("PURCHASE_ORDER"), eq("PO-000001"), eq(1L));

        // Lần 2: Nhận nốt phần còn lại (item1 nhận 4, item2 nhận 12)
        PurchaseOrderReceiveRequest reqFull = new PurchaseOrderReceiveRequest(
                "IDEM-PART-2",
                "Nhận nốt đợt 2",
                List.of(
                        new PurchaseOrderReceiveItemRequest(11L, 4),
                        new PurchaseOrderReceiveItemRequest(12L, 12)
                )
        );

        PurchaseOrderResponse resp2 = purchaseOrderService.receivePurchaseOrder(1L, reqFull, 1L);
        assertEquals(PurchaseOrderStatus.RECEIVED, resp2.status());
        assertEquals(10, item1.getQuantityReceived());
        assertEquals(20, item2.getQuantityReceived());

        verify(inventoryService).receiveStock(eq(101L), eq(1L), eq(4), eq(MovementType.PO_RECEIVE), any(), eq("PURCHASE_ORDER"), eq("PO-000001"), eq(1L));
        verify(inventoryService).receiveStock(eq(102L), eq(1L), eq(12), eq(MovementType.PO_RECEIVE), any(), eq("PURCHASE_ORDER"), eq("PO-000001"), eq(1L));
    }

    @Test
    @DisplayName("Nghiệp vụ 2: Idempotent - Gửi lại cùng idempotencyKey trả kết quả cũ và KHÔNG tăng tồn kho lần hai")
    void receivePurchaseOrder_idempotent_returnsExistingWithoutStockIncrease() {
        PurchaseOrder po = new PurchaseOrder("PO-000001", testSupplier, testBranch);
        po.setId(1L);
        po.setStatus(PurchaseOrderStatus.ORDERED);

        PurchaseOrderReceipt existingReceipt = new PurchaseOrderReceipt(po, testUser, "IDEM-DUPLICATE", "Giao dịch cũ");
        when(purchaseOrderReceiptRepository.findByIdempotencyKey("IDEM-DUPLICATE"))
                .thenReturn(Optional.of(existingReceipt));

        PurchaseOrderReceiveRequest dupReq = new PurchaseOrderReceiveRequest(
                "IDEM-DUPLICATE",
                "Gửi lại mạng lag",
                List.of(new PurchaseOrderReceiveItemRequest(11L, 5))
        );

        PurchaseOrderResponse resp = purchaseOrderService.receivePurchaseOrder(1L, dupReq, 1L);
        assertNotNull(resp);
        assertEquals("PO-000001", resp.poNumber());

        // Tuyệt đối không gọi receiveStock thêm lần nào
        verify(inventoryService, never()).receiveStock(any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Nghiệp vụ 3: Nhận vượt số lượng đặt ném lỗi 409 ConflictException và KHÔNG thay đổi tồn kho")
    void receivePurchaseOrder_exceedsQuantityOrdered_throws409AndNoStockChange() {
        PurchaseOrder po = new PurchaseOrder("PO-000001", testSupplier, testBranch);
        po.setId(1L);
        po.setStatus(PurchaseOrderStatus.ORDERED);

        PurchaseOrderItem item1 = new PurchaseOrderItem(po, variant1, 10, BigDecimal.valueOf(100000));
        item1.setId(11L);
        item1.setQuantityReceived(5); // Đã nhận 5
        po.getItems().add(item1);

        when(purchaseOrderReceiptRepository.findByIdempotencyKey("IDEM-OVER")).thenReturn(Optional.empty());
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));

        // Nhận thêm 6 (5 + 6 = 11 > 10)
        PurchaseOrderReceiveRequest overReq = new PurchaseOrderReceiveRequest(
                "IDEM-OVER",
                "Nhận lố",
                List.of(new PurchaseOrderReceiveItemRequest(11L, 6))
        );

        ConflictException ex = assertThrows(ConflictException.class, () ->
                purchaseOrderService.receivePurchaseOrder(1L, overReq, 1L)
        );

        assertTrue(ex.getMessage().contains("vượt quá số lượng đặt"));
        verify(inventoryService, never()).receiveStock(any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Nghiệp vụ 4: Nhận hàng khi PO đang ở trạng thái DRAFT hoặc CANCELLED ném lỗi 409")
    void receivePurchaseOrder_whenDraftOrCancelled_throws409() {
        PurchaseOrder draftPo = new PurchaseOrder("PO-000001", testSupplier, testBranch);
        draftPo.setId(1L);
        draftPo.setStatus(PurchaseOrderStatus.DRAFT);

        when(purchaseOrderReceiptRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(draftPo));

        PurchaseOrderReceiveRequest req = new PurchaseOrderReceiveRequest(
                "IDEM-1", "Test", List.of(new PurchaseOrderReceiveItemRequest(11L, 5)));

        assertThrows(ConflictException.class, () ->
                purchaseOrderService.receivePurchaseOrder(1L, req, 1L)
        );

        draftPo.setStatus(PurchaseOrderStatus.CANCELLED);
        assertThrows(ConflictException.class, () ->
                purchaseOrderService.receivePurchaseOrder(1L, req, 1L)
        );
    }

    @Test
    @DisplayName("Nghiệp vụ 5: Hủy đơn PO ở trạng thái ORDERED trả lại incoming về mức trước khi đặt")
    void cancelPurchaseOrder_whenOrdered_releasesIncoming() {
        PurchaseOrder po = new PurchaseOrder("PO-000001", testSupplier, testBranch);
        po.setId(1L);
        po.setStatus(PurchaseOrderStatus.ORDERED);

        PurchaseOrderItem item1 = new PurchaseOrderItem(po, variant1, 10, BigDecimal.valueOf(100000));
        item1.setId(11L);
        PurchaseOrderItem item2 = new PurchaseOrderItem(po, variant2, 25, BigDecimal.valueOf(90000));
        item2.setId(12L);
        po.getItems().addAll(List.of(item1, item2));

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderResponse resp = purchaseOrderService.cancelPurchaseOrder(1L);
        assertEquals(PurchaseOrderStatus.CANCELLED, resp.status());

        verify(inventoryService).releaseIncoming(eq(101L), eq(1L), eq(10), eq("PURCHASE_ORDER"), eq("PO-000001"));
        verify(inventoryService).releaseIncoming(eq(102L), eq(1L), eq(25), eq("PURCHASE_ORDER"), eq("PO-000001"));
    }

    @Test
    @DisplayName("Nghiệp vụ 6: total_cost do server tính toán từ subtotal và taxTotal, không tin giá trị client")
    void createPurchaseOrder_calculatesTotalCostOnServer() {
        when(supplierRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(testSupplier));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(purchaseOrderRepository.findMaxId()).thenReturn(5L);
        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(variant1));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> {
            PurchaseOrder saved = inv.getArgument(0);
            saved.setId(6L);
            return saved;
        });

        // 5 chiếc x 100,000 = 500,000. Thuế: 50,000. Tổng: 550,000.
        PurchaseOrderCreateRequest req = new PurchaseOrderCreateRequest(
                1L, 1L, "REF-123", "Ghi chú", "Net 30", "VND", List.of("ao-thun"),
                BigDecimal.valueOf(50000),
                List.of(new PurchaseOrderItemRequest(101L, 5, BigDecimal.valueOf(100000)))
        );

        PurchaseOrderResponse resp = purchaseOrderService.createPurchaseOrder(req);
        assertEquals("PO-000006", resp.poNumber());
        assertEquals(BigDecimal.valueOf(500000), resp.subtotalCost());
        assertEquals(BigDecimal.valueOf(50000), resp.taxTotal());
        assertEquals(BigDecimal.valueOf(550000), resp.totalCost());
    }

    @Test
    @DisplayName("Nghiệp vụ 7: Xóa nhà cung cấp còn PO chưa đóng (ORDERED hoặc PARTIALLY_RECEIVED) ném lỗi 409")
    void deleteSupplier_withActiveOrders_throws409() {
        when(supplierRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(testSupplier));
        when(purchaseOrderRepository.existsBySupplierIdAndStatusNotIn(eq(1L), any())).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                supplierService.deleteSupplier(1L)
        );

        assertTrue(ex.getMessage().contains("vẫn còn đơn đặt hàng chưa đóng"));
        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    @DisplayName("Nghiệp vụ 8: Đóng đơn PO khi PARTIALLY_RECEIVED trả lại số incoming chưa hoàn tất")
    void closePurchaseOrder_partiallyReceived_releasesUnfulfilledIncoming() {
        PurchaseOrder po = new PurchaseOrder("PO-000001", testSupplier, testBranch);
        po.setId(1L);
        po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);

        PurchaseOrderItem item1 = new PurchaseOrderItem(po, variant1, 10, BigDecimal.valueOf(100000));
        item1.setId(11L);
        item1.setQuantityReceived(6); // Thiếu 4 chiếc chưa về

        po.getItems().add(item1);

        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderResponse resp = purchaseOrderService.closePurchaseOrder(1L);
        assertEquals(PurchaseOrderStatus.CLOSED, resp.status());

        // Giải phóng đúng 4 chiếc incoming chưa về
        verify(inventoryService).releaseIncoming(eq(101L), eq(1L), eq(4), eq("PURCHASE_ORDER"), eq("PO-000001"));
    }
}
