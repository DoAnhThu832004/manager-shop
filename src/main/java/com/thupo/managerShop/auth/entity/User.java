package com.thupo.managerShop.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tài khoản người dùng.
 *
 * <p>Các bộ đếm (failed_login_count, lock_level, token_version) được cập nhật bằng câu lệnh UPDATE nguyên tử
 * trong {@code UserRepository}, không đọc-sửa-ghi qua entity, để tránh mất cập nhật khi nhiều request đồng thời.
 * Cột {@code version} chỉ bảo vệ các thay đổi thông thường (đổi mật khẩu, đổi thông tin).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "lock_until")
    private Instant lockUntil;

    @Column(name = "lock_level", nullable = false)
    private int lockLevel;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        normalize();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
        normalize();
    }

    /** Username và email luôn lưu chữ thường để so khớp không phân biệt hoa/thường. */
    private void normalize() {
        if (username != null) {
            username = username.trim().toLowerCase(Locale.ROOT);
        }
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }

    /** Tài khoản đang bị khoá tạm thời tại thời điểm {@code now}. */
    public boolean isTemporarilyLocked(Instant now) {
        return lockUntil != null && lockUntil.isAfter(now);
    }
}
