package com.thupo.managerShop.product.repository;

import com.thupo.managerShop.product.entity.Collection;
import com.thupo.managerShop.product.entity.CollectionType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, Long>, JpaSpecificationExecutor<Collection> {

    Optional<Collection> findByIdAndDeletedAtIsNull(Long id);

    @Query("SELECT c FROM Collection c WHERE c.deletedAt IS NULL " +
           "AND (:collectionType IS NULL OR c.collectionType = :collectionType) " +
           "AND (:q IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Collection> searchCollections(@Param("q") String q,
                                       @Param("collectionType") CollectionType collectionType,
                                       Pageable pageable);
}
