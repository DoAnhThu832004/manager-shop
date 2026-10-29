package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.ProductMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductMediaRepository extends JpaRepository<ProductMedia, Long> {
    List<ProductMedia> findByProductIdOrderByPositionAscIdAsc(Long productId);
}
