package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.IpBlock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IpBlockRepository extends JpaRepository<IpBlock, Long> {

    Optional<IpBlock> findByIpAddress(String ipAddress);

    /** Danh sách IP đang bị chặn (vĩnh viễn hoặc chưa hết hạn), dùng nạp cache cho IpBlockFilter. */
    @Query("select b from IpBlock b where b.blockedUntil is null or b.blockedUntil > :now")
    List<IpBlock> findActive(@Param("now") Instant now);

    @Modifying
    @Query("delete from IpBlock b where b.blockedUntil is not null and b.blockedUntil < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);

    @Modifying
    long deleteByIpAddress(String ipAddress);
}
