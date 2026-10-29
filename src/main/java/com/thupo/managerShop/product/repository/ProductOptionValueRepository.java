package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.ProductOptionValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, Long> {
    List<ProductOptionValue> findByOptionIdOrderByPositionAscIdAsc(Long optionId);
}
