package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.ProductOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    List<ProductOption> findByProductIdOrderByPositionAscIdAsc(Long productId);
}
