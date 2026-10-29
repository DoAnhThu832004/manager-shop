package com.thupo.managerShop.supplychain.service;

import com.thupo.managerShop.supplychain.dto.InventoryLevelResponse;
import com.thupo.managerShop.supplychain.dto.InventoryMovementResponse;
import com.thupo.managerShop.supplychain.entity.InventoryLevel;
import com.thupo.managerShop.supplychain.entity.InventoryMovement;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi giữa Entity và DTO cho Module Tồn kho.
 */
@Component
public class InventoryMapper {

    public InventoryLevelResponse toLevelResponse(InventoryLevel level) {
        if (level == null) return null;
        var variant = level.getVariant();
        var product = (variant != null) ? variant.getProduct() : null;
        var location = level.getLocation();

        return new InventoryLevelResponse(
                level.getId(),
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTitle() : null,
                variant != null ? variant.getSku() : null,
                product != null ? product.getId() : null,
                product != null ? product.getTitle() : null,
                location != null ? location.getId() : null,
                location != null ? location.getName() : null,
                level.getAvailable(),
                level.getOnHand(),
                level.getCommitted(),
                level.getUnavailable(),
                level.getIncoming(),
                level.getUpdatedAt()
        );
    }

    public InventoryMovementResponse toMovementResponse(InventoryMovement movement) {
        if (movement == null) return null;
        var variant = movement.getVariant();
        var location = movement.getLocation();
        var user = movement.getCreatedBy();

        return new InventoryMovementResponse(
                movement.getId(),
                variant != null ? variant.getId() : null,
                variant != null ? variant.getTitle() : null,
                variant != null ? variant.getSku() : null,
                location != null ? location.getId() : null,
                location != null ? location.getName() : null,
                movement.getMovementType(),
                movement.getQuantityDelta(),
                movement.getAvailableBefore(),
                movement.getAvailableAfter(),
                movement.getOnHandBefore(),
                movement.getOnHandAfter(),
                movement.getReason(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                user != null ? user.getId() : null,
                user != null ? user.getFullName() : null,
                movement.getCreatedAt()
        );
    }
}
