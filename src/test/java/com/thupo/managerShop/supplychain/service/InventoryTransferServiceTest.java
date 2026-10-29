package com.thupo.managerShop.supplychain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.repository.BranchRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.repository.ProductVariantRepository;
import com.thupo.managerShop.supplychain.dto.InventoryTransferCreateRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferItemRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferReceiveItemRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferReceiveRequest;
import com.thupo.managerShop.supplychain.dto.InventoryTransferResponse;
import com.thupo.managerShop.supplychain.entity.InventoryLevel;
import com.thupo.managerShop.supplychain.entity.InventoryTransfer;
import com.thupo.managerShop.supplychain.entity.InventoryTransferItem;
import com.thupo.managerShop.supplychain.entity.InventoryTransferReceipt;
import com.thupo.managerShop.supplychain.entity.MovementType;
import com.thupo.managerShop.supplychain.entity.TransferStatus;
import com.thupo.managerShop.supplychain.repository.InventoryLevelRepository;
import com.thupo.managerShop.supplychain.repository.InventoryTransferItemRepository;
import com.thupo.managerShop.supplychain.repository.InventoryTransferReceiptRepository;
import com.thupo.managerShop.supplychain.repository.InventoryTransferRepository;
import com.thupo.managerShop.supplychain.repository.PurchaseOrderRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryTransferServiceTest {

    @Mock
    private InventoryTransferRepository inventoryTransferRepository;
    @Mock
    private InventoryTransferItemRepository inventoryTransferItemRepository;
    @Mock
    private InventoryTransferReceiptRepository inventoryTransferReceiptRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InventoryLevelRepository inventoryLevelRepository;
    @Mock
    private InventoryService inventoryService;

    private TransferMapper transferMapper;
    private InventoryTransferService inventoryTransferService;

    private Branch originBranch;
    private Branch destBranch;
    private Product testProduct;
    private ProductVariant variant1;
    private ProductVariant variant2;
    private ProductVariant untrackedVariant;
    private User testUser;

    @BeforeEach
    void setUp() {
        transferMapper = new TransferMapper(new ObjectMapper());
        inventoryTransferService = new InventoryTransferService(
                inventoryTransferRepository,
                inventoryTransferItemRepository,
                inventoryTransferReceiptRepository,
                branchRepository,
                productVariantRepository,
                purchaseOrderRepository,
                userRepository,
                inventoryLevelRepository,
                inventoryService,
                transferMapper
        );

        originBranch = new Branch();
        originBranch.setId(1L);
        originBranch.setName("Kho Quận 1");
        originBranch.setCode("CN-Q1");

        destBranch = new Branch();
        destBranch.setId(2L);
        destBranch.setName("Kho Hà Nội");
        destBranch.setCode("CN-HN");

        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setTitle("Áo thun");
        testProduct.setStatus(ProductStatus.ACTIVE);

        variant1 = new ProductVariant();
        variant1.setId(101L);
        variant1.setProduct(testProduct);
        variant1.setTitle("Đen / L");
        variant1.setSku("SKU-BLK-L");
        variant1.setInventoryTracked(true);

        variant2 = new ProductVariant();
        variant2.setId(102L);
        variant2.setProduct(testProduct);
        variant2.setTitle("Trắng / M");
        variant2.setSku("SKU-WHT-M");
        variant2.setInventoryTracked(true);

        untrackedVariant = new ProductVariant();
        untrackedVariant.setId(103L);
        untrackedVariant.setProduct(testProduct);
        untrackedVariant.setTitle("Vàng / S");
        untrackedVariant.setSku("SKU-YEL-S");
        untrackedVariant.setInventoryTracked(false);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("admin@shop.com");
    }

    @Test
    @DisplayName("Nghiệp vụ 1: Kho nguồn thiếu hàng: send trả 409 INSUFFICIENT_STOCK và tồn kho 2 kho không đổi")
    void sendTransfer_originInsufficientStock_throws409AndStockUnchanged() {
        InventoryTransfer transfer = new InventoryTransfer("TR-000001", originBranch, destBranch);
        transfer.setId(1L);
        transfer.setStatus(TransferStatus.DRAFT);

        InventoryTransferItem item = new InventoryTransferItem(transfer, variant1, 10);
        item.setId(21L);
        transfer.getItems().add(item);

        when(inventoryTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));

        // Kho nguồn chỉ có sẵn 5 chiếc (yêu cầu chuyển 10)
        InventoryLevel originLevel = new InventoryLevel(variant1, originBranch);
        originLevel.setAvailable(5);
        originLevel.setOnHand(5);

        when(inventoryLevelRepository.findByVariantIdAndLocationId(101L, 1L))
                .thenReturn(Optional.of(originLevel));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                inventoryTransferService.sendTransfer(1L, 1L)
        );

        assertTrue(ex.getMessage().contains("INSUFFICIENT_STOCK"));
        verify(inventoryService, never()).issueStock(any(), any(), anyInt(), any(), any(), any(), any(), any());
        verify(inventoryService, never()).reserveIncoming(any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Nghiệp vụ 2: Sau send: kho nguồn issueStock, kho đích reserveIncoming, trạng thái IN_TRANSIT")
    void sendTransfer_success_originDecreases_destinationIncomingIncreases() {
        InventoryTransfer transfer = new InventoryTransfer("TR-000001", originBranch, destBranch);
        transfer.setId(1L);
        transfer.setStatus(TransferStatus.DRAFT);

        InventoryTransferItem item = new InventoryTransferItem(transfer, variant1, 15);
        item.setId(21L);
        transfer.getItems().add(item);

        when(inventoryTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));

        // Kho nguồn có sẵn 20 chiếc (chuyển 15)
        InventoryLevel originLevel = new InventoryLevel(variant1, originBranch);
        originLevel.setAvailable(20);
        originLevel.setOnHand(20);

        when(inventoryLevelRepository.findByVariantIdAndLocationId(101L, 1L))
                .thenReturn(Optional.of(originLevel));
        when(inventoryTransferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTransferResponse resp = inventoryTransferService.sendTransfer(1L, 1L);
        assertEquals(TransferStatus.IN_TRANSIT, resp.status());

        // Kho nguồn bị trừ kho
        verify(inventoryService).issueStock(eq(101L), eq(1L), eq(15), eq(MovementType.TRANSFER_OUT), any(), eq("TRANSFER"), eq("TR-000001"), eq(1L));
        // Kho đích tăng incoming
        verify(inventoryService).reserveIncoming(eq(101L), eq(2L), eq(15), eq("TRANSFER"), eq("TR-000001"));
    }

    @Test
    @DisplayName("Nghiệp vụ 3: Nhận một phần rồi nhận nốt: trạng thái PARTIALLY_RECEIVED -> COMPLETED")
    void receiveTransfer_partialThenFull_transitionsToCompleted() {
        InventoryTransfer transfer = new InventoryTransfer("TR-000001", originBranch, destBranch);
        transfer.setId(1L);
        transfer.setStatus(TransferStatus.IN_TRANSIT);

        InventoryTransferItem item1 = new InventoryTransferItem(transfer, variant1, 10);
        item1.setId(21L);
        InventoryTransferItem item2 = new InventoryTransferItem(transfer, variant2, 20);
        item2.setId(22L);
        transfer.getItems().addAll(List.of(item1, item2));

        when(inventoryTransferReceiptRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(inventoryTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(inventoryTransferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        // Lần 1: Nhận một phần (item1 nhận 6, item2 nhận 12)
        InventoryTransferReceiveRequest reqPart = new InventoryTransferReceiveRequest(
                "IDEM-TR-1", "Nhận đợt 1", List.of(
                new InventoryTransferReceiveItemRequest(21L, 6),
                new InventoryTransferReceiveItemRequest(22L, 12)
        ));

        InventoryTransferResponse resp1 = inventoryTransferService.receiveTransfer(1L, reqPart, 1L);
        assertEquals(TransferStatus.PARTIALLY_RECEIVED, resp1.status());
        assertEquals(6, item1.getQuantityReceived());
        assertEquals(12, item2.getQuantityReceived());

        verify(inventoryService).receiveStock(eq(101L), eq(2L), eq(6), eq(MovementType.TRANSFER_IN), any(), eq("TRANSFER"), eq("TR-000001"), eq(1L));
        verify(inventoryService).receiveStock(eq(102L), eq(2L), eq(12), eq(MovementType.TRANSFER_IN), any(), eq("TRANSFER"), eq("TR-000001"), eq(1L));

        // Lần 2: Nhận nốt phần còn lại (item1 nhận 4, item2 nhận 8)
        InventoryTransferReceiveRequest reqFull = new InventoryTransferReceiveRequest(
                "IDEM-TR-2", "Nhận đợt 2", List.of(
                new InventoryTransferReceiveItemRequest(21L, 4),
                new InventoryTransferReceiveItemRequest(22L, 8)
        ));

        InventoryTransferResponse resp2 = inventoryTransferService.receiveTransfer(1L, reqFull, 1L);
        assertEquals(TransferStatus.COMPLETED, resp2.status());
        assertEquals(10, item1.getQuantityReceived());
        assertEquals(20, item2.getQuantityReceived());

        verify(inventoryService).receiveStock(eq(101L), eq(2L), eq(4), eq(MovementType.TRANSFER_IN), any(), eq("TRANSFER"), eq("TR-000001"), eq(1L));
        verify(inventoryService).receiveStock(eq(102L), eq(2L), eq(8), eq(MovementType.TRANSFER_IN), any(), eq("TRANSFER"), eq("TR-000001"), eq(1L));
    }

    @Test
    @DisplayName("Nghiệp vụ 4: Idempotent - Gửi lại cùng idempotencyKey không làm tăng kho đích lần hai")
    void receiveTransfer_idempotent_returnsExistingWithoutStockIncrease() {
        InventoryTransfer transfer = new InventoryTransfer("TR-000001", originBranch, destBranch);
        transfer.setId(1L);
        transfer.setStatus(TransferStatus.IN_TRANSIT);

        InventoryTransferReceipt existingReceipt = new InventoryTransferReceipt(transfer, testUser, "IDEM-TR-DUP", "Giao dịch cũ");
        when(inventoryTransferReceiptRepository.findByIdempotencyKey("IDEM-TR-DUP"))
                .thenReturn(Optional.of(existingReceipt));

        InventoryTransferReceiveRequest dupReq = new InventoryTransferReceiveRequest(
                "IDEM-TR-DUP", "Nhận lại", List.of(new InventoryTransferReceiveItemRequest(21L, 5)));

        InventoryTransferResponse resp = inventoryTransferService.receiveTransfer(1L, dupReq, 1L);
        assertNotNull(resp);
        assertEquals("TR-000001", resp.transferNumber());

        verify(inventoryService, never()).receiveStock(any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Nghiệp vụ 5: Nhận vượt quá số lượng cần chuyển ném lỗi 409")
    void receiveTransfer_exceedsQuantityTransferred_throws409() {
        InventoryTransfer transfer = new InventoryTransfer("TR-000001", originBranch, destBranch);
        transfer.setId(1L);
        transfer.setStatus(TransferStatus.IN_TRANSIT);

        InventoryTransferItem item = new InventoryTransferItem(transfer, variant1, 10);
        item.setId(21L);
        item.setQuantityReceived(6); // Đã nhận 6
        transfer.getItems().add(item);

        when(inventoryTransferReceiptRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(inventoryTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));

        // Nhận thêm 5 (6 + 5 = 11 > 10)
        InventoryTransferReceiveRequest overReq = new InventoryTransferReceiveRequest(
                "IDEM-TR-OVER", "Nhận quá", List.of(new InventoryTransferReceiveItemRequest(21L, 5)));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                inventoryTransferService.receiveTransfer(1L, overReq, 1L)
        );

        assertTrue(ex.getMessage().contains("vượt quá số lượng cần chuyển"));
        verify(inventoryService, never()).receiveStock(any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Nghiệp vụ 6: Hủy khi IN_TRANSIT hoàn đúng tồn kho nguồn và trả incoming kho đích về 0")
    void cancelTransfer_inTransit_revertsStockToOriginAndReleasesIncoming() {
        InventoryTransfer transfer = new InventoryTransfer("TR-000001", originBranch, destBranch);
        transfer.setId(1L);
        transfer.setStatus(TransferStatus.IN_TRANSIT);

        InventoryTransferItem item1 = new InventoryTransferItem(transfer, variant1, 10);
        item1.setId(21L);
        InventoryTransferItem item2 = new InventoryTransferItem(transfer, variant2, 15);
        item2.setId(22L);
        transfer.getItems().addAll(List.of(item1, item2));

        when(inventoryTransferRepository.findById(1L)).thenReturn(Optional.of(transfer));
        when(inventoryTransferRepository.save(any(InventoryTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryTransferResponse resp = inventoryTransferService.cancelTransfer(1L, 1L);
        assertEquals(TransferStatus.CANCELLED, resp.status());

        // Hoàn lại kho nguồn
        verify(inventoryService).receiveStock(eq(101L), eq(1L), eq(10), eq(MovementType.TRANSFER_IN), any(), eq("TRANSFER"), eq("TR-000001"), eq(1L));
        verify(inventoryService).receiveStock(eq(102L), eq(1L), eq(15), eq(MovementType.TRANSFER_IN), any(), eq("TRANSFER"), eq("TR-000001"), eq(1L));

        // Giải phóng incoming kho đích
        verify(inventoryService).releaseIncoming(eq(101L), eq(2L), eq(10), eq("TRANSFER"), eq("TR-000001"));
        verify(inventoryService).releaseIncoming(eq(102L), eq(2L), eq(15), eq("TRANSFER"), eq("TR-000001"));
    }

    @Test
    @DisplayName("Nghiệp vụ 7: origin_location trùng destination_location ném lỗi 400 BadRequest")
    void createTransfer_sameOriginAndDestination_throws400() {
        InventoryTransferCreateRequest req = new InventoryTransferCreateRequest(
                1L, 1L, null, "Trùng kho", null, List.of(), null,
                List.of(new InventoryTransferItemRequest(101L, 5))
        );

        assertThrows(BadRequestAlertException.class, () ->
                inventoryTransferService.createTransfer(req)
        );
    }

    @Test
    @DisplayName("Nghiệp vụ 8: Biến thể không theo dõi tồn kho (inventory_tracked=false) ném lỗi 409")
    void createTransfer_untrackedVariant_throws409() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(originBranch));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(destBranch));
        when(inventoryTransferRepository.findMaxId()).thenReturn(0L);
        when(productVariantRepository.findById(103L)).thenReturn(Optional.of(untrackedVariant));

        InventoryTransferCreateRequest req = new InventoryTransferCreateRequest(
                1L, 2L, null, "Chuyển hàng", null, List.of(), null,
                List.of(new InventoryTransferItemRequest(103L, 5))
        );

        ConflictException ex = assertThrows(ConflictException.class, () ->
                inventoryTransferService.createTransfer(req)
        );

        assertTrue(ex.getMessage().contains("không được cấu hình theo dõi tồn kho"));
    }
}
