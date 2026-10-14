package com.thupo.managerShop.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** IP hoặc dải CIDR (mạng nội bộ cửa hàng) không bao giờ bị chặn. */
@Entity
@Table(name = "ip_whitelist")
@Getter
@Setter
@NoArgsConstructor
public class IpWhitelist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_or_cidr", nullable = false, length = 49)
    private String ipOrCidr;

    @Column(name = "note", length = 255)
    private String note;
}
