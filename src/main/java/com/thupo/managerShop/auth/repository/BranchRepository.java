package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.Branch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<Branch> findByCodeIgnoreCase(String code);
}
