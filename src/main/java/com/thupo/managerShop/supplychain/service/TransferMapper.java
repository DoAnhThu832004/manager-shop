package com.thupo.managerShop.supplychain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.supplychain.dto.InventoryTransferItemResponse;
import com.thupo.managerShop.supplychain.dto.InventoryTransferReceiptItemResponse;
import com.thupo.managerShop.supplychain.dto.InventoryTransferReceiptResponse;
import com.thupo.managerShop.supplychain.dto.InventoryTransferResponse;
import com.thupo.managerShop.supplychain.entity.InventoryTransfer;
import com.thupo.managerShop.supplychain.entity.InventoryTransferItem;
import com.thupo.managerShop.supplychain.entity.InventoryTransferReceipt;
import com.thupo.managerShop.supplychain.entity.InventoryTransferReceiptItem;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

    private final ObjectMapper objectMapper;

    public TransferMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public InventoryTransferResponse toTransferResponse(InventoryTransfer transfer) {
        if (transfer == null) return null;

        List<InventoryTransferItemResponse> itemResponses = transfer.getItems() != null
                ? transfer.getItems().stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        List<InventoryTransferReceiptResponse> receiptResponses = transfer.getReceipts() != null
                ? transfer.getReceipts().stream().map(this::toReceiptResponse).toList()
                : Collections.emptyList();

        return new InventoryTransferResponse(
                transfer.getId(),
                transfer.getTransferNumber(),
                transfer.getOriginLocation() != null ? transfer.getOriginLocation().getId() : null,
                transfer.getOriginLocation() != null ? transfer.getOriginLocation().getName() : null,
                transfer.getDestinationLocation() != null ? transfer.getDestinationLocation().getId() : null,
                transfer.getDestinationLocation() != null ? transfer.getDestinationLocation().getName() : null,
                transfer.getTransferDate(),
                transfer.getReferenceName(),
                transfer.getNote(),
                parseTags(transfer.getTags()),
                transfer.getLinkedPurchaseOrder() != null ? transfer.getLinkedPurchaseOrder().getId() : null,
                transfer.getLinkedPurchaseOrder() != null ? transfer.getLinkedPurchaseOrder().getPoNumber() : null,
                transfer.getStatus(),
                itemResponses,
                receiptResponses,
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }

    public InventoryTransferItemResponse toItemResponse(InventoryTransferItem item) {
        if (item == null) return null;
        var variant = item.getVariant();
        var product = (variant != null) ? variant.getProduct() : null;

        return new InventoryTransferItemResponse(
                item.getId(),
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTitle() : null,
                variant != null ? variant.getSku() : null,
                product != null ? product.getId() : null,
                product != null ? product.getTitle() : null,
                item.getQuantityTransferred(),
                item.getQuantityReceived()
        );
    }

    public InventoryTransferReceiptResponse toReceiptResponse(InventoryTransferReceipt receipt) {
        if (receipt == null) return null;

        List<InventoryTransferReceiptItemResponse> itemResponses = receipt.getReceiptItems() != null
                ? receipt.getReceiptItems().stream().map(this::toReceiptItemResponse).toList()
                : Collections.emptyList();

        return new InventoryTransferReceiptResponse(
                receipt.getId(),
                receipt.getTransfer() != null ? receipt.getTransfer().getId() : null,
                receipt.getReceivedBy() != null ? receipt.getReceivedBy().getId() : null,
                receipt.getReceivedBy() != null ? receipt.getReceivedBy().getEmail() : null,
                receipt.getReceivedAt(),
                receipt.getIdempotencyKey(),
                receipt.getNote(),
                itemResponses
        );
    }

    public InventoryTransferReceiptItemResponse toReceiptItemResponse(InventoryTransferReceiptItem item) {
        if (item == null) return null;
        var transferItem = item.getItem();
        var variant = (transferItem != null) ? transferItem.getVariant() : null;

        return new InventoryTransferReceiptItemResponse(
                item.getId(),
                transferItem != null ? transferItem.getId() : null,
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTitle() : null,
                variant != null ? variant.getSku() : null,
                item.getQuantity()
        );
    }

    public List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (Exception e) {
            return "[]";
        }
    }
}
