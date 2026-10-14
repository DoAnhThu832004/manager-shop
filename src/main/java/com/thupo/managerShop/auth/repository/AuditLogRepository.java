package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.AuditLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Modifying
    @Query("delete from AuditLog a where a.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);
}
