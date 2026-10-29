package com.thupo.managerShop.supplychain.dto;

import java.time.Instant;

public record SupplierResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address,
        String contactName,
        Instant createdAt,
        Instant updatedAt
) {}
