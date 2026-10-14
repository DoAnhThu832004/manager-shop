package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
