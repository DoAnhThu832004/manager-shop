package com.thupo.managerShop.giftcard.service;

import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.common.dto.ApiResponse;
import com.thupo.managerShop.common.dto.PageMeta;
import com.thupo.managerShop.common.exception.BadRequestAlertException;
import com.thupo.managerShop.common.exception.ConflictException;
import com.thupo.managerShop.common.exception.ResourceNotFoundException;
import com.thupo.managerShop.giftcard.config.GiftCardProperties;
import com.thupo.managerShop.giftcard.dto.GiftCardCreateRequest;
import com.thupo.managerShop.giftcard.dto.GiftCardCreatedResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardRedeemResult;
import com.thupo.managerShop.giftcard.dto.GiftCardResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardTransactionResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardUpdateRequest;
import com.thupo.managerShop.giftcard.entity.GiftCard;
import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import com.thupo.managerShop.giftcard.entity.GiftCardTransaction;
import com.thupo.managerShop.giftcard.entity.GiftCardTransactionType;
import com.thupo.managerShop.giftcard.repository.GiftCardRepository;
import com.thupo.managerShop.giftcard.repository.GiftCardTransactionRepository;
import com.thupo.managerShop.giftcard.util.GiftCardCodeUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service xử lý nghiệp vụ Thẻ quà tặng (Gift Cards).
 */
@Service
public class GiftCardService {

    private static final Logger log = LoggerFactory.getLogger(GiftCardService.class);
    private static final int MAX_GENERATE_RETRIES = 5;
    private static final int MAX_OPTIMISTIC_LOCK_RETRIES = 3;

    private final GiftCardRepository giftCardRepository;
    private final GiftCardTransactionRepository giftCardTransactionRepository;
    private final UserRepository userRepository;
    private final GiftCardProperties giftCardProperties;
    private final GiftCardMapper giftCardMapper;
    private final GiftCardService self;

    public GiftCardService(GiftCardRepository giftCardRepository,
                           GiftCardTransactionRepository giftCardTransactionRepository,
                           UserRepository userRepository,
                           GiftCardProperties giftCardProperties,
                           GiftCardMapper giftCardMapper,
                           @Lazy GiftCardService self) {
        this.giftCardRepository = giftCardRepository;
        this.giftCardTransactionRepository = giftCardTransactionRepository;
        this.userRepository = userRepository;
        this.giftCardProperties = giftCardProperties;
        this.giftCardMapper = giftCardMapper;
        this.self = self;
    }

    /**
     * Tạo mới thẻ quà tặng.
     * Response trả về GiftCardCreatedResponse chứa mã code gốc ĐÚNG MỘT LẦN.
     */
    @Transactional
    public GiftCardCreatedResponse createGiftCard(GiftCardCreateRequest request, Long currentUserId) {
        if (request.initialValue() == null || request.initialValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestAlertException("Mệnh giá ban đầu phải lớn hơn 0");
        }

        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw new BadRequestAlertException("Ngày hết hạn phải ở tương lai");
        }

        String rawCode;
        String codeHash;
        String pepper = giftCardProperties.getPepper();

        if (request.code() != null && !request.code().isBlank()) {
            // Mã do người dùng nhập: chuẩn hóa chữ thường, kiểm tra 8-20 ký tự chữ và số
            rawCode = GiftCardCodeUtil.normalizeCode(request.code());
            codeHash = GiftCardCodeUtil.computeHash(rawCode, pepper);
            if (giftCardRepository.existsByCodeHash(codeHash)) {
                throw new ConflictException("Mã thẻ quà tặng đã tồn tại trên hệ thống");
            }
        } else {
            // Hệ thống tự động sinh mã 16 ký tự an toàn, thử lại tối đa 5 lần nếu trùng hash
            String candidate = null;
            String hash = null;
            boolean foundUnique = false;

            for (int i = 0; i < MAX_GENERATE_RETRIES; i++) {
                candidate = GiftCardCodeUtil.generateCode();
                hash = GiftCardCodeUtil.computeHash(candidate, pepper);
                if (!giftCardRepository.existsByCodeHash(hash)) {
                    foundUnique = true;
                    break;
                }
            }

            if (!foundUnique) {
                throw new IllegalStateException("Không thể sinh mã thẻ quà tặng duy nhất sau " + MAX_GENERATE_RETRIES + " lần thử!");
            }
            rawCode = candidate;
            codeHash = hash;
        }

        String codeLast4 = GiftCardCodeUtil.extractLast4(rawCode);
        User user = resolveUser(currentUserId);

        GiftCard card = new GiftCard(
                codeHash,
                codeLast4,
                request.initialValue(),
                request.initialValue(),
                "VND",
                request.expiresAt(),
                request.customerId(),
                request.notes(),
                GiftCardStatus.ACTIVE,
                user
        );

        card = giftCardRepository.save(card);

        // Ghi nhận giao dịch phát hành ban đầu (ISSUE)
        GiftCardTransaction issueTx = new GiftCardTransaction(
                card,
                GiftCardTransactionType.ISSUE,
                request.initialValue(),
                request.initialValue(),
                null,
                null,
                user
        );
        issueTx = giftCardTransactionRepository.save(issueTx);
        card.getTransactions().add(issueTx);

        return giftCardMapper.toCreatedResponse(card, rawCode);
    }

    /**
     * Lấy thông tin chi tiết thẻ quà tặng theo ID (kèm lịch sử giao dịch).
     * Tuyệt đối chỉ trả về maskedCode.
     */
    @Transactional
    public GiftCardResponse getGiftCard(Long id) {
        GiftCard card = giftCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ quà tặng với id: " + id));

        checkAndUpdateExpired(card);
        return giftCardMapper.toResponse(card);
    }

    /**
     * Tra cứu danh sách thẻ quà tặng có lọc theo trạng thái, khách hàng, 4 số cuối và phân trang.
     */
    @Transactional
    public ApiResponse<List<GiftCardResponse>> getGiftCards(GiftCardStatus status,
                                                           Long customerId,
                                                           String last4,
                                                           int page,
                                                           int size,
                                                           String sort) {
        int pageNumber = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);

        Sort sortOrder = parseSort(sort);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortOrder);

        Page<GiftCard> pageResult = giftCardRepository.searchGiftCards(status, customerId, last4, pageable);

        // Kiểm tra hết hạn cho các thẻ trong trang
        List<GiftCardResponse> items = pageResult.getContent().stream()
                .peek(this::checkAndUpdateExpired)
                .map(giftCardMapper::toResponse)
                .toList();

        PageMeta meta = new PageMeta(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );

        return ApiResponse.of(items, meta);
    }

    /**
     * Cập nhật thông tin thẻ quà tặng (chỉ cho phép sửa expiresAt, customerId, notes).
     */
    @Transactional
    public GiftCardResponse updateGiftCard(Long id, GiftCardUpdateRequest request) {
        GiftCard card = giftCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ quà tặng với id: " + id));

        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw new BadRequestAlertException("Ngày hết hạn phải ở tương lai");
        }

        card.setNotes(request.notes());
        card.setCustomerId(request.customerId());
        card.setExpiresAt(request.expiresAt());
        card.setUpdatedAt(Instant.now());

        // Kiểm tra lại trạng thái sau khi cập nhật ngày hết hạn
        checkAndUpdateExpired(card);

        card = giftCardRepository.save(card);
        return giftCardMapper.toResponse(card);
    }

    /**
     * Vô hiệu hóa thẻ quà tặng.
     * Thao tác là idempotent: gọi lại trên thẻ đã DISABLED vẫn trả về thành công mà không trừ/ghi thêm giao dịch.
     */
    @Transactional
    public GiftCardResponse disableGiftCard(Long id, Long currentUserId) {
        GiftCard card = giftCardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ quà tặng với id: " + id));

        if (card.getStatus() == GiftCardStatus.DISABLED) {
            // Idempotent: đã vô hiệu hóa thì trả về kết quả hiện tại
            return giftCardMapper.toResponse(card);
        }

        User user = resolveUser(currentUserId);
        card.setStatus(GiftCardStatus.DISABLED);
        card.setDisabledAt(Instant.now());
        card.setUpdatedAt(Instant.now());

        GiftCardTransaction disableTx = new GiftCardTransaction(
                card,
                GiftCardTransactionType.DISABLE,
                BigDecimal.ZERO,
                card.getCurrentBalance(),
                null,
                null,
                user
        );
        disableTx = giftCardTransactionRepository.save(disableTx);
        card.getTransactions().add(disableTx);

        card = giftCardRepository.save(card);
        return giftCardMapper.toResponse(card);
    }

    /**
     * Phương thức nội bộ: Khấu trừ số dư thẻ quà tặng để thanh toán (REDEEM).
     * Dành cho module đơn hàng gọi sau này.
     *
     * Quy tắc:
     * - Chỉ áp dụng cho thẻ ACTIVE; DISABLED, EXPIRED, DEPLETED đều trả 409 Conflict.
     * - amount > 0 và amount <= current_balance; vi phạm trả 409 Conflict.
     * - Sử dụng khóa lạc quan @Version với cơ chế retry tối đa 3 lần khi xảy ra xung đột.
     * - Khi số dư về 0 thì status = DEPLETED.
     * - Idempotent: Nếu cùng idempotencyKey đã xử lý thành công, trả về kết quả lần đầu mà không trừ thêm.
     */
    public GiftCardRedeemResult redeem(String rawCode,
                                       BigDecimal amount,
                                       String orderRef,
                                       String idempotencyKey,
                                       Long currentUserId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Số tiền thanh toán phải lớn hơn 0");
        }

        // Kiểm tra tính Idempotent trước khi thực hiện
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<GiftCardTransaction> existingTx = giftCardTransactionRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existingTx.isPresent()) {
                GiftCardTransaction tx = existingTx.get();
                return new GiftCardRedeemResult(
                        tx.getGiftCard().getId(),
                        tx.getAmount(),
                        tx.getBalanceAfter(),
                        tx.getGiftCard().getStatus(),
                        tx.getIdempotencyKey()
                );
            }
        }

        String normalizedCode = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalizedCode, giftCardProperties.getPepper());

        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_RETRIES; attempt++) {
            try {
                return self.executeRedeemInNewTx(codeHash, amount, orderRef, idempotencyKey, currentUserId);
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                    throw new ConflictException("Xung đột đồng thời khi thanh toán bằng thẻ quà tặng. Vui lòng thử lại");
                }
                try {
                    Thread.sleep(15L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Xung đột đồng thời khi thanh toán bằng thẻ quà tặng");
                }
            }
        }
        throw new ConflictException("Không thể hoàn tất thanh toán bằng thẻ quà tặng");
    }

    /**
     * Thực thi một lượt khấu trừ trong Transaction độc lập để phục vụ retry khóa lạc quan.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GiftCardRedeemResult executeRedeemInNewTx(String codeHash,
                                                     BigDecimal amount,
                                                     String orderRef,
                                                     String idempotencyKey,
                                                     Long currentUserId) {
        GiftCard card = giftCardRepository.findByCodeHash(codeHash)
                .orElseThrow(() -> new ConflictException("Thẻ quà tặng không tồn tại hoặc không hợp lệ"));

        checkAndUpdateExpired(card);

        if (card.getStatus() != GiftCardStatus.ACTIVE) {
            throw new ConflictException("Thẻ quà tặng không ở trạng thái hoạt động: " + card.getStatus());
        }

        if (amount.compareTo(card.getCurrentBalance()) > 0) {
            throw new ConflictException("Số dư thẻ quà tặng không đủ (số dư hiện tại: " + card.getCurrentBalance() + ")");
        }

        BigDecimal newBalance = card.getCurrentBalance().subtract(amount);
        card.setCurrentBalance(newBalance);
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            card.setStatus(GiftCardStatus.DEPLETED);
        }
        card.setUpdatedAt(Instant.now());

        User user = resolveUser(currentUserId);
        String finalIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;

        GiftCardTransaction redeemTx = new GiftCardTransaction(
                card,
                GiftCardTransactionType.REDEEM,
                amount,
                newBalance,
                orderRef,
                finalIdempotencyKey,
                user
        );

        giftCardRepository.saveAndFlush(card);
        giftCardTransactionRepository.saveAndFlush(redeemTx);

        return new GiftCardRedeemResult(
                card.getId(),
                amount,
                newBalance,
                card.getStatus(),
                finalIdempotencyKey
        );
    }

    /**
     * Phương thức nội bộ: Hoàn lại số dư vào thẻ quà tặng (REFUND).
     *
     * Quy tắc:
     * - Số dư hoàn tối đa bằng initial_value; vượt quá trả 409 Conflict.
     * - Thẻ ở trạng thái DEPLETED khi được hoàn tiền (và chưa hết hạn) sẽ quay lại trạng thái ACTIVE.
     * - Thẻ DISABLED không cho phép hoàn tiền (409 Conflict).
     * - Idempotent theo idempotencyKey.
     */
    public GiftCardRedeemResult refund(String rawCode,
                                       BigDecimal amount,
                                       String orderRef,
                                       String idempotencyKey,
                                       Long currentUserId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Số tiền hoàn phải lớn hơn 0");
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<GiftCardTransaction> existingTx = giftCardTransactionRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existingTx.isPresent()) {
                GiftCardTransaction tx = existingTx.get();
                return new GiftCardRedeemResult(
                        tx.getGiftCard().getId(),
                        tx.getAmount(),
                        tx.getBalanceAfter(),
                        tx.getGiftCard().getStatus(),
                        tx.getIdempotencyKey()
                );
            }
        }

        String normalizedCode = GiftCardCodeUtil.normalizeCode(rawCode);
        String codeHash = GiftCardCodeUtil.computeHash(normalizedCode, giftCardProperties.getPepper());

        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_LOCK_RETRIES; attempt++) {
            try {
                return self.executeRefundInNewTx(codeHash, amount, orderRef, idempotencyKey, currentUserId);
            } catch (OptimisticLockingFailureException e) {
                if (attempt == MAX_OPTIMISTIC_LOCK_RETRIES) {
                    throw new ConflictException("Xung đột đồng thời khi hoàn tiền thẻ quà tặng. Vui lòng thử lại");
                }
                try {
                    Thread.sleep(15L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Xung đột đồng thời khi hoàn tiền thẻ quà tặng");
                }
            }
        }
        throw new ConflictException("Không thể hoàn tất hoàn tiền thẻ quà tặng");
    }

    /**
     * Thực thi hoàn tiền trong Transaction độc lập.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GiftCardRedeemResult executeRefundInNewTx(String codeHash,
                                                     BigDecimal amount,
                                                     String orderRef,
                                                     String idempotencyKey,
                                                     Long currentUserId) {
        GiftCard card = giftCardRepository.findByCodeHash(codeHash)
                .orElseThrow(() -> new ConflictException("Thẻ quà tặng không tồn tại hoặc không hợp lệ"));

        if (card.getStatus() == GiftCardStatus.DISABLED) {
            throw new ConflictException("Thẻ quà tặng đã bị vô hiệu hóa, không thể hoàn tiền");
        }

        BigDecimal newBalance = card.getCurrentBalance().add(amount);
        if (newBalance.compareTo(card.getInitialValue()) > 0) {
            throw new ConflictException("Số tiền hoàn (" + amount + ") khiến số dư vượt quá giá trị ban đầu của thẻ (" + card.getInitialValue() + ")");
        }

        card.setCurrentBalance(newBalance);

        // Nếu thẻ đang DEPLETED và chưa quá hạn thì khôi phục lại trạng thái ACTIVE
        if (card.getStatus() == GiftCardStatus.DEPLETED && newBalance.compareTo(BigDecimal.ZERO) > 0) {
            boolean isExpired = card.getExpiresAt() != null && card.getExpiresAt().isBefore(Instant.now());
            card.setStatus(isExpired ? GiftCardStatus.EXPIRED : GiftCardStatus.ACTIVE);
        }
        card.setUpdatedAt(Instant.now());

        User user = resolveUser(currentUserId);
        String finalIdempotencyKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? idempotencyKey.trim() : null;

        GiftCardTransaction refundTx = new GiftCardTransaction(
                card,
                GiftCardTransactionType.REFUND,
                amount,
                newBalance,
                orderRef,
                finalIdempotencyKey,
                user
        );

        giftCardRepository.saveAndFlush(card);
        giftCardTransactionRepository.saveAndFlush(refundTx);

        return new GiftCardRedeemResult(
                card.getId(),
                amount,
                newBalance,
                card.getStatus(),
                finalIdempotencyKey
        );
    }

    /**
     * Scheduled job chạy hàng giờ (0 phút mỗi giờ) để tự động cập nhật các thẻ đã quá hạn thành EXPIRED.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoExpireCards() {
        int updated = giftCardRepository.updateExpiredCards(Instant.now());
        if (updated > 0) {
            log.info("Đã tự động cập nhật {} thẻ quà tặng hết hạn thành trạng thái EXPIRED", updated);
        }
    }

    private void checkAndUpdateExpired(GiftCard card) {
        if (card.getStatus() == GiftCardStatus.ACTIVE
                && card.getExpiresAt() != null
                && card.getExpiresAt().isBefore(Instant.now())) {
            card.setStatus(GiftCardStatus.EXPIRED);
            card.setUpdatedAt(Instant.now());
            giftCardRepository.save(card);
        }
    }

    private User resolveUser(Long userId) {
        if (userId != null) {
            return userRepository.findById(userId)
                    .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
        }
        return userRepository.findAll().stream().findFirst().orElse(null);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        return switch (field) {
            case "current_balance", "currentBalance" -> Sort.by(direction, "currentBalance");
            case "initial_value", "initialValue" -> Sort.by(direction, "initialValue");
            default -> Sort.by(direction, "createdAt");
        };
    }
}
