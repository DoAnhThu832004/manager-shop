package com.thupo.managerShop.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** IP bị chặn. {@code blockedUntil == null} nghĩa là chặn vĩnh viễn (chờ admin gỡ). */
@Entity
@Table(name = "ip_blocklist")
@Getter
@Setter
@NoArgsConstructor
public class IpBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "block_level", nullable = false)
    private int blockLevel = 1;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy = "SYSTEM";

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** IP còn bị chặn tại thời điểm {@code now} hay không. */
    public boolean isActive(Instant now) {
        return blockedUntil == null || blockedUntil.isAfter(now);
    }
}
