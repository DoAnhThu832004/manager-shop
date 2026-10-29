package com.thupo.managerShop.supplychain.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderItemResponse;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiptItemResponse;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderReceiptResponse;
import com.thupo.managerShop.supplychain.dto.PurchaseOrderResponse;
import com.thupo.managerShop.supplychain.dto.SupplierResponse;
import com.thupo.managerShop.supplychain.entity.PurchaseOrder;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderItem;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderReceipt;
import com.thupo.managerShop.supplychain.entity.PurchaseOrderReceiptItem;
import com.thupo.managerShop.supplychain.entity.Supplier;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PurchaseOrderMapper {

    private final ObjectMapper objectMapper;

    public PurchaseOrderMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SupplierResponse toSupplierResponse(Supplier supplier) {
        if (supplier == null) return null;
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getAddress(),
                supplier.getContactName(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }

    public PurchaseOrderResponse toPurchaseOrderResponse(PurchaseOrder po) {
        if (po == null) return null;

        List<PurchaseOrderItemResponse> itemResponses = po.getItems() != null
                ? po.getItems().stream().map(this::toPurchaseOrderItemResponse).toList()
                : Collections.emptyList();

        List<PurchaseOrderReceiptResponse> receiptResponses = po.getReceipts() != null
                ? po.getReceipts().stream().map(this::toPurchaseOrderReceiptResponse).toList()
                : Collections.emptyList();

        return new PurchaseOrderResponse(
                po.getId(),
                po.getPoNumber(),
                po.getSupplier() != null ? po.getSupplier().getId() : null,
                po.getSupplier() != null ? po.getSupplier().getName() : null,
                po.getDestinationLocation() != null ? po.getDestinationLocation().getId() : null,
                po.getDestinationLocation() != null ? po.getDestinationLocation().getName() : null,
                po.getReferenceNumber(),
                po.getNoteToSupplier(),
                po.getPaymentTerms(),
                po.getCurrency(),
                parseTags(po.getTags()),
                po.getStatus(),
                po.getSubtotalCost(),
                po.getTaxTotal(),
                po.getTotalCost(),
                po.getOrderedAt(),
                itemResponses,
                receiptResponses,
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    public PurchaseOrderItemResponse toPurchaseOrderItemResponse(PurchaseOrderItem item) {
        if (item == null) return null;
        var variant = item.getVariant();
        var product = (variant != null) ? variant.getProduct() : null;
        BigDecimal lineTotal = item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantityOrdered()));

        return new PurchaseOrderItemResponse(
                item.getId(),
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTitle() : null,
                variant != null ? variant.getSku() : null,
                product != null ? product.getId() : null,
                product != null ? product.getTitle() : null,
                item.getQuantityOrdered(),
                item.getQuantityReceived(),
                item.getUnitCost(),
                lineTotal
        );
    }

    public PurchaseOrderReceiptResponse toPurchaseOrderReceiptResponse(PurchaseOrderReceipt receipt) {
        if (receipt == null) return null;

        List<PurchaseOrderReceiptItemResponse> itemResponses = receipt.getReceiptItems() != null
                ? receipt.getReceiptItems().stream().map(this::toPurchaseOrderReceiptItemResponse).toList()
                : Collections.emptyList();

        return new PurchaseOrderReceiptResponse(
                receipt.getId(),
                receipt.getPurchaseOrder() != null ? receipt.getPurchaseOrder().getId() : null,
                receipt.getReceivedBy() != null ? receipt.getReceivedBy().getId() : null,
                receipt.getReceivedBy() != null ? receipt.getReceivedBy().getEmail() : null,
                receipt.getReceivedAt(),
                receipt.getIdempotencyKey(),
                receipt.getNote(),
                itemResponses
        );
    }

    public PurchaseOrderReceiptItemResponse toPurchaseOrderReceiptItemResponse(PurchaseOrderReceiptItem receiptItem) {
        if (receiptItem == null) return null;
        var poItem = receiptItem.getItem();
        var variant = (poItem != null) ? poItem.getVariant() : null;

        return new PurchaseOrderReceiptItemResponse(
                receiptItem.getId(),
                poItem != null ? poItem.getId() : null,
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTitle() : null,
                variant != null ? variant.getSku() : null,
                receiptItem.getQuantity()
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
