package com.thupo.managerShop.supplychain.dto;

import java.time.Instant;
import java.util.List;

public record PurchaseOrderReceiptResponse(
        Long id,
        Long purchaseOrderId,
        Long receivedByUserId,
        String receivedByEmail,
        Instant receivedAt,
        String idempotencyKey,
        String note,
        List<PurchaseOrderReceiptItemResponse> items
) {}
