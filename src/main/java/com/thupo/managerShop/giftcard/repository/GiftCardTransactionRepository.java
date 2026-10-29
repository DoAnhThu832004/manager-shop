package com.thupo.managerShop.giftcard.repository;

import com.thupo.managerShop.giftcard.entity.GiftCardTransaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GiftCardTransactionRepository extends JpaRepository<GiftCardTransaction, Long> {

    Optional<GiftCardTransaction> findByIdempotencyKey(String idempotencyKey);

    List<GiftCardTransaction> findByGiftCardIdOrderByCreatedAtDesc(Long giftCardId);
}
