package com.thupo.managerShop.giftcard.service;

import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.giftcard.config.GiftCardProperties;
import com.thupo.managerShop.giftcard.dto.GiftCardCreateRequest;
import com.thupo.managerShop.giftcard.dto.GiftCardCreatedResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardRedeemResult;
import com.thupo.managerShop.giftcard.dto.GiftCardResponse;
import com.thupo.managerShop.giftcard.entity.GiftCard;
import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import com.thupo.managerShop.giftcard.entity.GiftCardTransaction;
import com.thupo.managerShop.giftcard.entity.GiftCardTransactionType;
import com.thupo.managerShop.giftcard.repository.GiftCardRepository;
import com.thupo.managerShop.giftcard.repository.GiftCardTransactionRepository;
import com.thupo.managerShop.giftcard.util.GiftCardCodeUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GiftCardServiceTest {

    @Mock
    private GiftCardRepository giftCardRepository;

    @Mock
    private GiftCardTransactionRepository giftCardTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Environment environment;

    private GiftCardProperties giftCardProperties;
    private GiftCardMapper giftCardMapper;
    private GiftCardService giftCardService;

    private User testAdminUser;

    @BeforeEach
    void setUp() {
        giftCardProperties = new GiftCardProperties(environment);
        giftCardProperties.setPepper("test-unit-secret-pepper-2026");
        giftCardMapper = new GiftCardMapper();

        giftCardService = new GiftCardService(
                giftCardRepository,
                giftCardTransactionRepository,
                userRepository,
                giftCardProperties,
                giftCardMapper,
                null
        );

        // Inject self reference as giftCardService itself for internal retry
        try {
            java.lang.reflect.Field selfField = GiftCardService.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(giftCardService, giftCardService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        testAdminUser = new User();
        testAdminUser.setId(1L);
        testAdminUser.setUsername("admin");
    }

    @Test
    @DisplayName("Nghiệp vụ 1: Tạo thẻ không có code -> sinh mã 16 ký tự hợp lệ, DB không lưu mã gốc, GET chỉ có maskedCode")
    void createWithoutCode_generates16ValidChars_rawNotInDb_maskedInGet() {
        when(giftCardRepository.existsByCodeHash(anyString())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testAdminUser));
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> {
            GiftCard c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(giftCardTransactionRepository.save(any(GiftCardTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GiftCardCreateRequest request = new GiftCardCreateRequest(
                null,
                BigDecimal.valueOf(100000.00),
                null,
                null,
                "Tạo thẻ tự động"
        );

        GiftCardCreatedResponse created = giftCardService.createGiftCard(request, 1L);

        // Kiểm tra response tạo có mã gốc
        assertNotNull(created.code(), "Mã gốc phải có trong response tạo thẻ lần đầu");
        assertEquals(16, created.code().length(), "Mã sinh ra phải đúng 16 ký tự");
        // Không chứa ký tự dễ nhầm 0, o, 1, l, i
        assertFalse(created.code().contains("0"));
        assertFalse(created.code().contains("o"));
        assertFalse(created.code().contains("1"));
        assertFalse(created.code().contains("l"));
        assertFalse(created.code().contains("i"));

        // Kiểm tra Entity lưu xuống DB
        ArgumentCaptor<GiftCard> cardCaptor = ArgumentCaptor.forClass(GiftCard.class);
        verify(giftCardRepository).save(cardCaptor.capture());
        GiftCard savedCard = cardCaptor.getValue();
        assertEquals(64, savedCard.getCodeHash().length(), "DB chỉ lưu code_hash độ dài 64 hex");
        assertFalse(savedCard.getCodeHash().equals(created.code()), "DB tuyệt đối không lưu mã gốc");
        assertEquals(created.code().substring(12), savedCard.getCodeLast4());

        // Kiểm tra GET chỉ trả về maskedCode
        when(giftCardRepository.findById(100L)).thenReturn(Optional.of(savedCard));
        GiftCardResponse getResponse = giftCardService.getGiftCard(100L);
        assertEquals("************" + savedCard.getCodeLast4(), getResponse.maskedCode());
    }

    @Test
    @DisplayName("Nghiệp vụ 2: Tạo thẻ với mã trùng -> ném ConflictException (409)")
    void createWithDuplicateCode_throwsConflictException409() {
        when(giftCardRepository.existsByCodeHash(anyString())).thenReturn(true);

        GiftCardCreateRequest request = new GiftCardCreateRequest(
                "customcode123",
                BigDecimal.valueOf(50000.00),
                null,
                null,
                "Mã tùy chọn bị trùng"
        );

        ConflictException ex = assertThrows(ConflictException.class, () ->
                giftCardService.createGiftCard(request, 1L));
        assertTrue(ex.getMessage().contains("đã tồn tại"));
        verify(giftCardRepository, never()).save(any());
    }

    @Test
    @DisplayName("Nghiệp vụ 3: Doesn't expire lưu expires_at = NULL; ngày hết hạn trong quá khứ trả 400")
    void create_doesntExpireSetsNull_pastDateThrowsBadRequest400() {
        when(giftCardRepository.existsByCodeHash(anyString())).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testAdminUser));
        when(giftCardRepository.save(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardTransactionRepository.save(any(GiftCardTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Doesn't expire (expiresAt = null)
        GiftCardCreateRequest nullExpireRequest = new GiftCardCreateRequest(
                null,
                BigDecimal.valueOf(20000.00),
                null,
                null,
                "Không bao giờ hết hạn"
        );
        GiftCardCreatedResponse resp = giftCardService.createGiftCard(nullExpireRequest, 1L);
        assertNull(resp.expiresAt(), "expiresAt phải là null khi chọn doesn't expire");

        // 2. Ngày hết hạn ở quá khứ -> ném BadRequestAlertException
        Instant pastDate = Instant.now().minus(2, ChronoUnit.DAYS);
        GiftCardCreateRequest pastExpireRequest = new GiftCardCreateRequest(
                null,
                BigDecimal.valueOf(20000.00),
                pastDate,
                null,
                "Ngày quá khứ"
        );
        assertThrows(BadRequestAlertException.class, () ->
                giftCardService.createGiftCard(pastExpireRequest, 1L));
    }

    @Test
    @DisplayName("Nghiệp vụ 4: Redeem một phần -> số dư giảm đúng, trạng thái vẫn ACTIVE")
    void redeem_partial_decreasesBalance_remainsActive() {
        String rawCode = "abcdef23456789gh";
        String normalized = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalized, giftCardProperties.getPepper());

        GiftCard card = new GiftCard(codeHash, "89gh", BigDecimal.valueOf(1000.00), BigDecimal.valueOf(1000.00),
                "VND", null, null, null, GiftCardStatus.ACTIVE, testAdminUser);
        card.setId(10L);

        when(giftCardTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(card));
        when(giftCardRepository.saveAndFlush(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardTransactionRepository.saveAndFlush(any(GiftCardTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GiftCardRedeemResult result = giftCardService.redeem(rawCode, BigDecimal.valueOf(400.00), "ORD-001", "IDEMP-1", 1L);

        assertEquals(0, BigDecimal.valueOf(600.00).compareTo(result.balanceAfter()));
        assertEquals(GiftCardStatus.ACTIVE, result.status());
        assertEquals(0, BigDecimal.valueOf(600.00).compareTo(card.getCurrentBalance()));
    }

    @Test
    @DisplayName("Nghiệp vụ 5: Redeem toàn bộ -> số dư về 0, trạng thái chuyển sang DEPLETED")
    void redeem_full_balanceZero_becomesDepleted() {
        String rawCode = "xyz23456789abcde";
        String normalized = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalized, giftCardProperties.getPepper());

        GiftCard card = new GiftCard(codeHash, "bcde", BigDecimal.valueOf(500.00), BigDecimal.valueOf(500.00),
                "VND", null, null, null, GiftCardStatus.ACTIVE, testAdminUser);
        card.setId(20L);

        when(giftCardTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(card));
        when(giftCardRepository.saveAndFlush(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardTransactionRepository.saveAndFlush(any(GiftCardTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        GiftCardRedeemResult result = giftCardService.redeem(rawCode, BigDecimal.valueOf(500.00), "ORD-002", "IDEMP-2", 1L);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.balanceAfter()));
        assertEquals(GiftCardStatus.DEPLETED, result.status());
        assertEquals(GiftCardStatus.DEPLETED, card.getStatus());
    }

    @Test
    @DisplayName("Nghiệp vụ 6: Redeem vượt quá số dư -> ném ConflictException (409) và số dư không đổi")
    void redeem_exceedingBalance_throwsConflict409_balanceUnchanged() {
        String rawCode = "testexcessredeem";
        String normalized = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalized, giftCardProperties.getPepper());

        GiftCard card = new GiftCard(codeHash, "deem", BigDecimal.valueOf(300.00), BigDecimal.valueOf(300.00),
                "VND", null, null, null, GiftCardStatus.ACTIVE, testAdminUser);

        when(giftCardTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(card));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                giftCardService.redeem(rawCode, BigDecimal.valueOf(400.00), "ORD-003", "IDEMP-3", 1L));

        assertTrue(ex.getMessage().contains("Số dư thẻ quà tặng không đủ"));
        assertEquals(0, BigDecimal.valueOf(300.00).compareTo(card.getCurrentBalance()), "Số dư phải giữ nguyên");
        verify(giftCardRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Nghiệp vụ 7: Redeem trên thẻ DISABLED hoặc EXPIRED -> ném ConflictException (409)")
    void redeem_onDisabledOrExpired_throwsConflict409() {
        String rawCode = "disabledandexpir";
        String normalized = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalized, giftCardProperties.getPepper());

        // 1. Thẻ DISABLED
        GiftCard disabledCard = new GiftCard(codeHash, "xpir", BigDecimal.valueOf(500.00), BigDecimal.valueOf(500.00),
                "VND", null, null, null, GiftCardStatus.DISABLED, testAdminUser);

        when(giftCardTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(disabledCard));

        ConflictException exDisabled = assertThrows(ConflictException.class, () ->
                giftCardService.redeem(rawCode, BigDecimal.valueOf(100.00), "ORD-004", "IDEMP-4", 1L));
        assertTrue(exDisabled.getMessage().contains("không ở trạng thái hoạt động"));

        // 2. Thẻ có ngày hết hạn trong quá khứ (tự động chuyển EXPIRED khi kiểm tra)
        GiftCard expiredCard = new GiftCard(codeHash, "xpir", BigDecimal.valueOf(500.00), BigDecimal.valueOf(500.00),
                "VND", Instant.now().minus(1, ChronoUnit.HOURS), null, null, GiftCardStatus.ACTIVE, testAdminUser);

        when(giftCardRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(expiredCard));

        ConflictException exExpired = assertThrows(ConflictException.class, () ->
                giftCardService.redeem(rawCode, BigDecimal.valueOf(100.00), "ORD-005", "IDEMP-5", 1L));
        assertTrue(exExpired.getMessage().contains("không ở trạng thái hoạt động") || exExpired.getMessage().contains("EXPIRED"));
        assertEquals(GiftCardStatus.EXPIRED, expiredCard.getStatus());
    }

    @Test
    @DisplayName("Nghiệp vụ 8: Idempotency -> cùng idempotencyKey chỉ trừ một lần, lần 2 trả lại kết quả lần 1")
    void redeem_sameIdempotencyKey_deductsOnlyOnce() {
        String rawCode = "idempotencytest1";
        String normalized = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalized, giftCardProperties.getPepper());

        GiftCard card = new GiftCard(codeHash, "est1", BigDecimal.valueOf(1000.00), BigDecimal.valueOf(1000.00),
                "VND", null, null, null, GiftCardStatus.ACTIVE, testAdminUser);
        card.setId(50L);

        // Lần 1: Chưa có idempotencyKey
        when(giftCardTransactionRepository.findByIdempotencyKey("KEY-REPEAT-99")).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(card));
        when(giftCardRepository.saveAndFlush(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardTransactionRepository.saveAndFlush(any(GiftCardTransaction.class))).thenAnswer(inv -> {
            GiftCardTransaction tx = inv.getArgument(0);
            tx.setId(777L);
            return tx;
        });

        GiftCardRedeemResult firstCall = giftCardService.redeem(rawCode, BigDecimal.valueOf(250.00), "ORD-99", "KEY-REPEAT-99", 1L);
        assertEquals(0, BigDecimal.valueOf(750.00).compareTo(firstCall.balanceAfter()));

        // Lần 2: Đã có giao dịch với idempotencyKey này
        GiftCardTransaction cachedTx = new GiftCardTransaction(
                card, GiftCardTransactionType.REDEEM, BigDecimal.valueOf(250.00), BigDecimal.valueOf(750.00), "ORD-99", "KEY-REPEAT-99", testAdminUser);
        when(giftCardTransactionRepository.findByIdempotencyKey("KEY-REPEAT-99")).thenReturn(Optional.of(cachedTx));

        GiftCardRedeemResult secondCall = giftCardService.redeem(rawCode, BigDecimal.valueOf(250.00), "ORD-99", "KEY-REPEAT-99", 1L);
        assertEquals(firstCall.balanceAfter(), secondCall.balanceAfter());
        assertEquals("KEY-REPEAT-99", secondCall.idempotencyKey());

        // Kiểm tra saveAndFlush chỉ được gọi đúng 1 lần
        verify(giftCardRepository, times(1)).saveAndFlush(any());
        verify(giftCardTransactionRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Nghiệp vụ 9: Concurrency Test -> Số dư 500, 10 luồng cùng redeem 100: đúng 5 thành công, số dư cuối bằng 0")
    void redeem_concurrent_500Balance_10ThreadsRedeeming100_exactly5Succeed_endingBalanceZero() throws Exception {
        String rawCode = "concurrencycard1";
        String normalized = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalized, giftCardProperties.getPepper());

        GiftCard sharedCard = new GiftCard(codeHash, "ard1", BigDecimal.valueOf(500.00), BigDecimal.valueOf(500.00),
                "VND", null, null, null, GiftCardStatus.ACTIVE, testAdminUser);
        sharedCard.setId(99L);

        ReentrantLock dbLock = new ReentrantLock();

        when(giftCardTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeHash(codeHash)).thenAnswer(inv -> {
            dbLock.lock();
            return Optional.of(sharedCard);
        });
        when(giftCardRepository.saveAndFlush(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardTransactionRepository.saveAndFlush(any(GiftCardTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger failCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    try {
                        giftCardService.redeem(rawCode, BigDecimal.valueOf(100.00), "ORD-CONC-" + index, "IDEMP-C-" + index, 1L);
                        successCounter.incrementAndGet();
                    } catch (ConflictException ce) {
                        failCounter.incrementAndGet();
                    } finally {
                        if (dbLock.isHeldByCurrentThread()) {
                            dbLock.unlock();
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
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Toàn bộ 10 threads phải hoàn thành");
        assertEquals(5, successCounter.get(), "Đúng 5 threads thành công khấu trừ");
        assertEquals(5, failCounter.get(), "Đúng 5 threads thất bại vì hết số dư");
        assertEquals(0, BigDecimal.ZERO.compareTo(sharedCard.getCurrentBalance()), "Số dư cuối cùng phải bằng 0");
        assertEquals(GiftCardStatus.DEPLETED, sharedCard.getStatus(), "Trạng thái cuối cùng phải là DEPLETED");
    }

    @Test
    @DisplayName("Nghiệp vụ 10: Refund -> thẻ DEPLETED có tiền hoàn quay lại ACTIVE, không được vượt quá initial_value")
    void refund_restoresDepletedCardToActive_cannotExceedInitialValue() {
        String rawCode = "refundtestcode12";
        String normalized = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalized, giftCardProperties.getPepper());

        GiftCard card = new GiftCard(codeHash, "de12", BigDecimal.valueOf(500.00), BigDecimal.ZERO,
                "VND", null, null, null, GiftCardStatus.DEPLETED, testAdminUser);
        card.setId(88L);

        when(giftCardTransactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(giftCardRepository.findByCodeHash(codeHash)).thenReturn(Optional.of(card));
        when(giftCardRepository.saveAndFlush(any(GiftCard.class))).thenAnswer(inv -> inv.getArgument(0));
        when(giftCardTransactionRepository.saveAndFlush(any(GiftCardTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. Hoàn 200 -> balance 200, status ACTIVE
        GiftCardRedeemResult refundResult = giftCardService.refund(rawCode, BigDecimal.valueOf(200.00), "ORD-REF-1", "REF-KEY-1", 1L);
        assertEquals(0, BigDecimal.valueOf(200.00).compareTo(refundResult.balanceAfter()));
        assertEquals(GiftCardStatus.ACTIVE, refundResult.status());
        assertEquals(GiftCardStatus.ACTIVE, card.getStatus());

        // 2. Hoàn vượt quá initial_value (500) -> ném ConflictException
        ConflictException ex = assertThrows(ConflictException.class, () ->
                giftCardService.refund(rawCode, BigDecimal.valueOf(400.00), "ORD-REF-2", "REF-KEY-2", 1L));
        assertTrue(ex.getMessage().contains("vượt quá giá trị ban đầu"));
    }
}
