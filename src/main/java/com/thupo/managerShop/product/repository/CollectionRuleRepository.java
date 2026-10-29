package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.CollectionRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionRuleRepository extends JpaRepository<CollectionRule, Long> {
    List<CollectionRule> findByCollectionIdOrderByIdAsc(Long collectionId);
    void deleteByCollectionId(Long collectionId);
}
