package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.LoginAttempt;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Dùng {@link JpaSpecificationExecutor} cho API admin lọc nhiều điều kiện tuỳ chọn. */
public interface LoginAttemptRepository
        extends JpaRepository<LoginAttempt, Long>, JpaSpecificationExecutor<LoginAttempt> {

    @Modifying
    @Query("delete from LoginAttempt a where a.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);

    @Modifying
    @Query(value = "DELETE FROM login_attempts WHERE created_at < :threshold LIMIT :limit", nativeQuery = true)
    int deleteBatchOlderThan(@Param("threshold") Instant threshold, @Param("limit") int limit);
}
