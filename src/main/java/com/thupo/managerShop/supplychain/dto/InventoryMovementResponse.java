package com.thupo.managerShop.supplychain.dto;

import com.thupo.managerShop.supplychain.entity.MovementType;
import java.time.Instant;

public record InventoryMovementResponse(
        Long id,
        Long variantId,
        String variantTitle,
        String sku,
        Long locationId,
        String locationName,
        MovementType movementType,
        int quantityDelta,
        int availableBefore,
        int availableAfter,
        int onHandBefore,
        int onHandAfter,
        String reason,
        String referenceType,
        String referenceId,
        Long createdById,
        String createdByName,
        Instant createdAt
) {}
