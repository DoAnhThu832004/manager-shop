package com.thupo.managerShop.supplychain.dto;

import java.time.Instant;
import java.util.List;

public record InventoryTransferReceiptResponse(
        Long id,
        Long transferId,
        Long receivedByUserId,
        String receivedByEmail,
        Instant receivedAt,
        String idempotencyKey,
        String note,
        List<InventoryTransferReceiptItemResponse> items
) {}
