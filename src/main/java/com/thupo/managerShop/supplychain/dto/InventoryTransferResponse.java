package com.thupo.managerShop.supplychain.dto;

import com.thupo.managerShop.supplychain.entity.TransferStatus;
import java.time.Instant;
import java.util.List;

public record InventoryTransferResponse(
        Long id,
        String transferNumber,
        Long originLocationId,
        String originLocationName,
        Long destinationLocationId,
        String destinationLocationName,
        Instant transferDate,
        String referenceName,
        String note,
        List<String> tags,
        Long linkedPurchaseOrderId,
        String linkedPoNumber,
        TransferStatus status,
        List<InventoryTransferItemResponse> items,
        List<InventoryTransferReceiptResponse> receipts,
        Instant createdAt,
        Instant updatedAt
) {}
