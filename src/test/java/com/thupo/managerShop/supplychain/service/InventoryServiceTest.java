package com.thupo.managerShop.supplychain.service;

import com.thupo.managerShop.auth.entity.Branch;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.repository.BranchRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.product.entity.Product;
import com.thupo.managerShop.product.entity.ProductStatus;
import com.thupo.managerShop.product.entity.ProductVariant;
import com.thupo.managerShop.product.repository.ProductVariantRepository;
import com.thupo.managerShop.supplychain.dto.InventoryAdjustmentItem;
import com.thupo.managerShop.supplychain.dto.InventoryAdjustmentRequest;
import com.thupo.managerShop.supplychain.dto.InventoryLevelResponse;
import com.thupo.managerShop.supplychain.entity.InventoryLevel;
import com.thupo.managerShop.supplychain.entity.InventoryMovement;
import com.thupo.managerShop.supplychain.entity.MovementType;
import com.thupo.managerShop.supplychain.repository.InventoryLevelRepository;
import com.thupo.managerShop.supplychain.repository.InventoryMovementRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryLevelRepository inventoryLevelRepository;
    @Mock
    private InventoryMovementRepository inventoryMovementRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private UserRepository userRepository;

    private InventoryMapper inventoryMapper;
    private InventoryService inventoryService;

    private Product testProduct;
    private ProductVariant trackedVariant;
    private ProductVariant untrackedVariant;
    private Branch testBranch;
    private User testUser;

    @BeforeEach
    void setUp() {
        inventoryMapper = new InventoryMapper();
        inventoryService = new InventoryService(
                inventoryLevelRepository,
                inventoryMovementRepository,
                productVariantRepository,
                branchRepository,
                userRepository,
                inventoryMapper
        );

        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setTitle("Áo thun");
        testProduct.setDescription("<p>Mô tả</p>");
        testProduct.setStatus(ProductStatus.ACTIVE);

        trackedVariant = new ProductVariant();
        trackedVariant.setId(101L);
        trackedVariant.setProduct(testProduct);
        trackedVariant.setTitle("Đen / L");
        trackedVariant.setSku("SKU-BLK-L");
        trackedVariant.setPrice(BigDecimal.valueOf(200000));
        trackedVariant.setInventoryTracked(true);

        untrackedVariant = new ProductVariant();
        untrackedVariant.setId(102L);
        untrackedVariant.setProduct(testProduct);
        untrackedVariant.setTitle("Trắng / S");
        untrackedVariant.setSku("SKU-WHT-S");
        untrackedVariant.setPrice(BigDecimal.valueOf(180000));
        untrackedVariant.setInventoryTracked(false);

        testBranch = new Branch();
        testBranch.setId(1L);
        testBranch.setName("Kho Tổng Hà Nội");
        testBranch.setCode("KHO-HN");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("admin@shop.com");
    }

    @Test
    @DisplayName("Nghiệp vụ 1: Đẳng thức kế toán kho luôn được bảo toàn on_hand = available + committed + unavailable")
    void adjustAvailable_maintainsAccountingEquation() {
        InventoryLevel level = new InventoryLevel(trackedVariant, testBranch);
        level.setId(1L);
        level.setAvailable(100);
        level.setCommitted(10);
        level.setUnavailable(5);
        level.setOnHand(115); // 100 + 10 + 5

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenReturn(Optional.of(level));
        when(inventoryLevelRepository.save(any(InventoryLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        // Điều chỉnh tăng +20
        InventoryLevelResponse respPlus = inventoryService.adjustAvailable(101L, 1L, 20, "Nhập kiểm kê", "ADJ", "ADJ-1", 1L);
        assertEquals(120, respPlus.available());
        assertEquals(135, respPlus.onHand());
        assertEquals(respPlus.onHand(), respPlus.available() + respPlus.committed() + respPlus.unavailable());

        // Điều chỉnh giảm -30
        InventoryLevelResponse respMinus = inventoryService.adjustAvailable(101L, 1L, -30, "Xuất bù", "ADJ", "ADJ-2", 1L);
        assertEquals(90, respMinus.available());
        assertEquals(105, respMinus.onHand());
        assertEquals(respMinus.onHand(), respMinus.available() + respMinus.committed() + respMinus.unavailable());
    }

    @Test
    @DisplayName("Nghiệp vụ 2: Trừ quá số lượng available ném lỗi 409 ConflictException và KHÔNG ghi movement")
    void adjustAvailable_deductMoreThanAvailable_throwsConflictExceptionAndNoMovement() {
        InventoryLevel level = new InventoryLevel(trackedVariant, testBranch);
        level.setId(1L);
        level.setAvailable(5);
        level.setOnHand(5);

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenReturn(Optional.of(level));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                inventoryService.adjustAvailable(101L, 1L, -10, "Xuất kho vượt tồn", "ORDER", "ORD-1", 1L)
        );

        assertTrue(ex.getMessage().contains("Không đủ tồn kho khả dụng"));
        verify(inventoryLevelRepository, never()).save(any(InventoryLevel.class));
        verify(inventoryMovementRepository, never()).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("Nghiệp vụ 3: Biến thể không theo dõi tồn kho (inventory_tracked=false) ném lỗi 409")
    void adjustAvailable_untrackedVariant_throwsConflictException() {
        when(productVariantRepository.findById(102L)).thenReturn(Optional.of(untrackedVariant));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                inventoryService.adjustAvailable(102L, 1L, 10, "Thử điều chỉnh", "TEST", "REF", 1L)
        );

        assertTrue(ex.getMessage().contains("không được cấu hình theo dõi tồn kho"));
        verify(inventoryLevelRepository, never()).save(any(InventoryLevel.class));
        verify(inventoryMovementRepository, never()).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("Nghiệp vụ 4: Điều chỉnh theo lô (Batch Adjustment) - nếu 1 dòng lỗi sẽ ném lỗi 409")
    void adjustBatch_failsEntirelyIfOneItemExceedsStock() {
        InventoryLevel level = new InventoryLevel(trackedVariant, testBranch);
        level.setId(1L);
        level.setAvailable(10);
        level.setOnHand(10);

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenReturn(Optional.of(level));
        when(inventoryLevelRepository.save(any(InventoryLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryAdjustmentRequest batchReq = new InventoryAdjustmentRequest(
                List.of(
                        new InventoryAdjustmentItem(101L, 1L, 5, null, "Hợp lệ +5"),
                        new InventoryAdjustmentItem(101L, 1L, -999, null, "Không hợp lệ -999")
                ),
                "BATCH-ADJ",
                "REF-001"
        );

        assertThrows(ConflictException.class, () ->
                inventoryService.adjustBatch(batchReq, 1L)
        );
    }

    @Test
    @DisplayName("Nghiệp vụ 5: Mỗi thay đổi tồn kho sinh đúng 1 dòng movement với số liệu snapshot chính xác")
    void adjustAvailable_recordsMovementWithAccurateSnapshots() {
        InventoryLevel level = new InventoryLevel(trackedVariant, testBranch);
        level.setId(1L);
        level.setAvailable(50);
        level.setOnHand(50);

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenReturn(Optional.of(level));
        when(inventoryLevelRepository.save(any(InventoryLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.adjustAvailable(101L, 1L, 15, "Kiểm kê định kỳ", "AUDIT", "AUD-100", 1L);

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());

        InventoryMovement savedMovement = captor.getValue();
        assertNotNull(savedMovement);
        assertEquals(MovementType.ADJUSTMENT, savedMovement.getMovementType());
        assertEquals(15, savedMovement.getQuantityDelta());
        assertEquals(50, savedMovement.getAvailableBefore());
        assertEquals(65, savedMovement.getAvailableAfter());
        assertEquals(50, savedMovement.getOnHandBefore());
        assertEquals(65, savedMovement.getOnHandAfter());
        assertEquals("Kiểm kê định kỳ", savedMovement.getReason());
        assertEquals("AUDIT", savedMovement.getReferenceType());
        assertEquals("AUD-100", savedMovement.getReferenceId());
        assertEquals(testUser, savedMovement.getCreatedBy());
    }

    @Test
    @DisplayName("Nghiệp vụ 6 Concurrency: 20 luồng đồng thời trừ 1 đơn vị từ tồn 10 -> đúng 10 luồng thành công, 10 thất bại")
    void adjustAvailable_concurrency20Threads_exactly10Succeed10Fail() throws Exception {
        int threadCount = 20;
        int initialStock = 10;

        InventoryLevel sharedLevel = new InventoryLevel(trackedVariant, testBranch);
        sharedLevel.setId(1L);
        sharedLevel.setAvailable(initialStock);
        sharedLevel.setOnHand(initialStock);

        ReentrantLock dbRowLock = new ReentrantLock();

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));

        // Mô phỏng SELECT ... FOR UPDATE khóa hàng độc quyền trong DB Transaction
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenAnswer(inv -> {
            dbRowLock.lock();
            return Optional.of(sharedLevel);
        });

        when(inventoryLevelRepository.save(any(InventoryLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    try {
                        inventoryService.adjustAvailable(101L, 1L, -1, "Đơn hàng đồng thời", "ORDER", "O-RACE", null);
                        successCounter.incrementAndGet();
                    } catch (ConflictException ce) {
                        failCounter.incrementAndGet();
                    } finally {
                        if (dbRowLock.isHeldByCurrentThread()) {
                            dbRowLock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Toàn bộ 20 threads phải kết thúc");
        assertEquals(10, successCounter.get(), "Đúng 10 threads thành công trừ hàng");
        assertEquals(10, failCounter.get(), "Đúng 10 threads thất bại do hết tồn kho");
        assertEquals(0, sharedLevel.getAvailable(), "Tồn kho kết thúc phải về đúng 0");
        assertEquals(0, sharedLevel.getOnHand(), "On hand kết thúc phải về đúng 0");

        // Ghi đúng 10 dòng biến động sổ cái
        verify(inventoryMovementRepository, times(10)).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("Nghiệp vụ 7: Nhập hàng receiveStock tăng available, on_hand và giảm incoming tương ứng")
    void receiveStock_increasesAvailableAndOnHand_decreasesIncoming() {
        InventoryLevel level = new InventoryLevel(trackedVariant, testBranch);
        level.setId(1L);
        level.setAvailable(10);
        level.setOnHand(10);
        level.setIncoming(20);

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenReturn(Optional.of(level));
        when(inventoryLevelRepository.save(any(InventoryLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        InventoryLevelResponse resp = inventoryService.receiveStock(
                101L, 1L, 15, MovementType.PO_RECEIVE, "Nhập từ PO-001", "PO", "PO-001", null);

        assertEquals(25, resp.available());
        assertEquals(25, resp.onHand());
        assertEquals(5, resp.incoming()); // 20 - 15 = 5

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryMovementRepository).save(captor.capture());
        assertEquals(MovementType.PO_RECEIVE, captor.getValue().getMovementType());
        assertEquals(15, captor.getValue().getQuantityDelta());
    }

    @Test
    @DisplayName("Nghiệp vụ 8: Xuất kho issueStock giảm available và on_hand, chặn nếu tồn kho không đủ")
    void issueStock_decreasesStock_andThrowsWhenInsufficient() {
        InventoryLevel level = new InventoryLevel(trackedVariant, testBranch);
        level.setId(1L);
        level.setAvailable(10);
        level.setOnHand(10);

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenReturn(Optional.of(level));
        when(inventoryLevelRepository.save(any(InventoryLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        // Xuất thành công 4 chiếc
        InventoryLevelResponse resp = inventoryService.issueStock(
                101L, 1L, 4, MovementType.TRANSFER_OUT, "Chuyển kho", "TRANSFER", "TR-1", null);
        assertEquals(6, resp.available());
        assertEquals(6, resp.onHand());

        // Xuất tiếp 10 chiếc (chỉ còn 6) -> ném ConflictException
        assertThrows(ConflictException.class, () ->
                inventoryService.issueStock(101L, 1L, 10, MovementType.TRANSFER_OUT, "Chuyển kho", "TRANSFER", "TR-2", null)
        );
    }

    @Test
    @DisplayName("Nghiệp vụ 9: reserveIncoming và releaseIncoming quản lý hàng trên đường về")
    void reserveAndReleaseIncoming() {
        InventoryLevel level = new InventoryLevel(trackedVariant, testBranch);
        level.setId(1L);
        level.setIncoming(0);

        when(productVariantRepository.findById(101L)).thenReturn(Optional.of(trackedVariant));
        when(branchRepository.findById(1L)).thenReturn(Optional.of(testBranch));
        when(inventoryLevelRepository.findByVariantIdAndLocationIdForUpdate(101L, 1L)).thenReturn(Optional.of(level));
        when(inventoryLevelRepository.save(any(InventoryLevel.class))).thenAnswer(inv -> inv.getArgument(0));

        inventoryService.reserveIncoming(101L, 1L, 50, "PO", "PO-1");
        assertEquals(50, level.getIncoming());

        inventoryService.releaseIncoming(101L, 1L, 20, "PO", "PO-1");
        assertEquals(30, level.getIncoming());
    }
}
