package com.thupo.managerShop.auth.repository;

import com.thupo.managerShop.auth.entity.PasswordHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    /** 3 mật khẩu gần nhất (mới nhất trước). */
    List<PasswordHistory> findTop3ByUserIdOrderByCreatedAtDesc(Long userId);
}
