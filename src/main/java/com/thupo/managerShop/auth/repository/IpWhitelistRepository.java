package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.IpWhitelist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IpWhitelistRepository extends JpaRepository<IpWhitelist, Long> {
}
