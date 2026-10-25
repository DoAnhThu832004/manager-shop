package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.RefreshToken;
import com.thupo.managerShop.auth.entity.RefreshTokenStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Khoá hàng ({@code SELECT ... FOR UPDATE}) để xử lý an toàn khi 2 request refresh đến cùng lúc. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Cập nhật trạng thái nguyên tử từ ACTIVE sang USED (Atomic Check-and-Set).
     * Loại bỏ sự cần thiết của SELECT ... FOR UPDATE, không gây block connection giữa các request.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.status = com.thupo.managerShop.auth.entity.RefreshTokenStatus.USED, "
            + "t.usedAt = :now where t.tokenHash = :tokenHash and t.status = com.thupo.managerShop.auth.entity.RefreshTokenStatus.ACTIVE")
    int markTokenAsUsedAtomic(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.status = com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED "
            + "where t.id in (:ids) and t.status <> com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED")
    int revokeByIdIn(@Param("ids") List<Long> ids);

    List<RefreshToken> findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtAsc(
            Long userId, RefreshTokenStatus status, Instant now);

    long countByUserIdAndStatusAndExpiresAtAfter(Long userId, RefreshTokenStatus status, Instant now);

    Optional<RefreshToken> findByIdAndUserId(Long id, Long userId);

    /** Thu hồi cả họ token (dùng khi phát hiện tái sử dụng hoặc đăng xuất một phiên). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.status = com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED "
            + "where t.familyId = :familyId and t.status <> com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED")
    int revokeFamily(@Param("familyId") String familyId);

    /** Thu hồi toàn bộ phiên của user (logout-all, đổi/đặt lại mật khẩu). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.status = com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED "
            + "where t.userId = :userId and t.status <> com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED")
    int revokeAllByUserId(@Param("userId") Long userId);

    Optional<RefreshToken> findFirstByFamilyIdAndStatusOrderByCreatedAtDesc(
            String familyId, RefreshTokenStatus status);

    List<RefreshToken> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, RefreshTokenStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.status = com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED "
            + "where t.id = :id and t.userId = :userId and t.status <> com.thupo.managerShop.auth.entity.RefreshTokenStatus.REVOKED")
    int revokeByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken t where t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
