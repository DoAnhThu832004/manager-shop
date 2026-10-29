package com.thupo.managerShop.giftcard.service;

import com.thupo.managerShop.giftcard.dto.GiftCardCreatedResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardResponse;
import com.thupo.managerShop.giftcard.dto.GiftCardTransactionResponse;
import com.thupo.managerShop.giftcard.entity.GiftCard;
import com.thupo.managerShop.giftcard.entity.GiftCardTransaction;
import com.thupo.managerShop.giftcard.util.GiftCardCodeUtil;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi giữa Entity và DTO cho Module Thẻ quà tặng.
 * Viết tay thuần túy theo quy ước kiến trúc dự án.
 */
@Component
public class GiftCardMapper {

    public GiftCardTransactionResponse toTransactionResponse(GiftCardTransaction tx) {
        if (tx == null) {
            return null;
        }
        return new GiftCardTransactionResponse(
                tx.getId(),
                tx.getType(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getOrderRef(),
                tx.getIdempotencyKey(),
                tx.getCreatedBy() != null ? tx.getCreatedBy().getId() : null,
                tx.getCreatedAt()
        );
    }

    public GiftCardResponse toResponse(GiftCard card) {
        if (card == null) {
            return null;
        }
        List<GiftCardTransactionResponse> txResponses = card.getTransactions() != null
                ? card.getTransactions().stream().map(this::toTransactionResponse).toList()
                : List.of();

        return new GiftCardResponse(
                card.getId(),
                GiftCardCodeUtil.maskCode(card.getCodeLast4()),
                card.getCodeLast4(),
                card.getInitialValue(),
                card.getCurrentBalance(),
                card.getCurrencyCode(),
                card.getExpiresAt(),
                card.getCustomerId(),
                card.getNotes(),
                card.getStatus(),
                card.getDisabledAt(),
                card.getCreatedBy() != null ? card.getCreatedBy().getId() : null,
                card.getVersion(),
                card.getCreatedAt(),
                card.getUpdatedAt(),
                txResponses
        );
    }

    public GiftCardCreatedResponse toCreatedResponse(GiftCard card, String rawCode) {
        if (card == null) {
            return null;
        }
        List<GiftCardTransactionResponse> txResponses = card.getTransactions() != null
                ? card.getTransactions().stream().map(this::toTransactionResponse).toList()
                : List.of();

        return new GiftCardCreatedResponse(
                card.getId(),
                rawCode,
                GiftCardCodeUtil.maskCode(card.getCodeLast4()),
                card.getCodeLast4(),
                card.getInitialValue(),
                card.getCurrentBalance(),
                card.getCurrencyCode(),
                card.getExpiresAt(),
                card.getCustomerId(),
                card.getNotes(),
                card.getStatus(),
                card.getCreatedBy() != null ? card.getCreatedBy().getId() : null,
                card.getVersion(),
                card.getCreatedAt(),
                card.getUpdatedAt(),
                txResponses
        );
    }
}
