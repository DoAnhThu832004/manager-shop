package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Tìm theo username hoặc email (đã chuẩn hoá lowercase), kèm roles, bỏ qua user đã xoá mềm. */
    @Query("select distinct u from User u left join fetch u.roles "
            + "where (u.username = :identifier or u.email = :identifier) and u.deletedAt is null")
    Optional<User> findByIdentifierWithRoles(@Param("identifier") String identifier);

    @Query("select u from User u left join fetch u.roles where u.id = :id and u.deletedAt is null")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select u.status as status, u.tokenVersion as tokenVersion, u.deletedAt as deletedAt "
            + "from User u where u.id = :id")
    Optional<UserAuthState> findAuthStateById(@Param("id") Long id);

    @Query("select u.failedLoginCount from User u where u.id = :id")
    Optional<Integer> findFailedLoginCount(@Param("id") Long id);

    /**
     * Tăng bộ đếm sai mật khẩu nguyên tử ở mức DB, tránh mất cập nhật khi nhiều request sai đồng thời.
     * Bulk update không đi qua {@code @Version}, nên không gây xung đột optimistic lock với luồng khác.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginCount = u.failedLoginCount + 1 where u.id = :id")
    int incrementFailedCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.failedLoginCount = 0 where u.id = :id")
    int resetFailedCount(@Param("id") Long id);

    /** Áp dụng khoá tạm: đặt thời điểm hết khoá, tăng cấp độ khoá, xoá bộ đếm sai. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lockUntil = :until, u.lockLevel = u.lockLevel + 1, u.failedLoginCount = 0 "
            + "where u.id = :id")
    int applyLock(@Param("id") Long id, @Param("until") Instant until);

    /** Mở khoá hoàn toàn (admin mở khoá hoặc đặt lại mật khẩu thành công). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lockUntil = null, u.lockLevel = 0, u.failedLoginCount = 0 where u.id = :id")
    int unlock(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lastLoginAt = :at, u.lastLoginIp = :ip where u.id = :id")
    int touchLastLogin(@Param("id") Long id, @Param("at") Instant at, @Param("ip") String ip);

    /** Tăng token_version để vô hiệu mọi access token đang còn hạn của user. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int bumpTokenVersion(@Param("id") Long id);
}
