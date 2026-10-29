package com.thupo.managerShop.giftcard.repository;

import com.thupo.managerShop.giftcard.entity.GiftCard;
import com.thupo.managerShop.giftcard.entity.GiftCardStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GiftCardRepository extends JpaRepository<GiftCard, Long> {

    Optional<GiftCard> findByCodeHash(String codeHash);

    boolean existsByCodeHash(String codeHash);

    @Query("SELECT g FROM GiftCard g WHERE "
            + "(:status IS NULL OR g.status = :status) AND "
            + "(:customerId IS NULL OR g.customerId = :customerId) AND "
            + "(:last4 IS NULL OR g.codeLast4 = :last4)")
    Page<GiftCard> searchGiftCards(@Param("status") GiftCardStatus status,
                                   @Param("customerId") Long customerId,
                                   @Param("last4") String last4,
                                   Pageable pageable);

    @Modifying
    @Query("UPDATE GiftCard g SET g.status = com.thupo.managerShop.giftcard.entity.GiftCardStatus.EXPIRED, g.updatedAt = :now "
            + "WHERE g.status = com.thupo.managerShop.giftcard.entity.GiftCardStatus.ACTIVE "
            + "AND g.expiresAt IS NOT NULL AND g.expiresAt <= :now")
    int updateExpiredCards(@Param("now") Instant now);
}
