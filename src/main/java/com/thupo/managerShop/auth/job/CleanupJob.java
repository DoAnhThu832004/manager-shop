package com.thupo.managerShop.auth.job;

import com.thupo.managerShop.auth.repository.AuditLogRepository;
import com.thupo.managerShop.auth.repository.IpBlockRepository;
import com.thupo.managerShop.auth.repository.LoginAttemptRepository;
import com.thupo.managerShop.auth.repository.PasswordResetTokenRepository;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job định kỳ dọn dẹp dữ liệu cũ và token hết hạn.
 * Được bảo vệ bởi ShedLock để bảo đảm an toàn khi chạy trên nhiều server instances.
 */
@Component
public class CleanupJob {

    private static final Logger log = LoggerFactory.getLogger(CleanupJob.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final IpBlockRepository ipBlockRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuditLogRepository auditLogRepository;

    public CleanupJob(RefreshTokenRepository refreshTokenRepository,
                      PasswordResetTokenRepository passwordResetTokenRepository,
                      IpBlockRepository ipBlockRepository,
                      LoginAttemptRepository loginAttemptRepository,
                      AuditLogRepository auditLogRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.ipBlockRepository = ipBlockRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Chạy định kỳ vào 02:00 sáng mỗi ngày.
     * lockAtLeastFor = 5 phút (tránh trường hợp job chạy quá nhanh dẫn đến node khác giành lock chạy lại)
     * lockAtMostFor = 30 phút (tự động nhả lock nếu node gặp sự cố crash đột ngột)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "auth_data_cleanup_job", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    @Transactional
    public void cleanupExpiredData() {
        log.info("Bắt đầu job định kỳ dọn dẹp dữ liệu xác thực cũ...");
        Instant now = Instant.now();

        try {
            // 1. Dọn dẹp refresh token đã hết hạn
            int deletedRefreshTokens = refreshTokenRepository.deleteExpiredBefore(now);
            log.info("Đã dọn dẹp {} refresh tokens hết hạn", deletedRefreshTokens);

            // 2. Dọn dẹp password reset token đã hết hạn
            int deletedResetTokens = passwordResetTokenRepository.deleteExpiredBefore(now);
            log.info("Đã dọn dẹp {} password reset tokens hết hạn", deletedResetTokens);

            // 3. Dọn dẹp IP bị chặn đã hết hạn thời gian
            int deletedIpBlocks = ipBlockRepository.deleteExpiredBefore(now);
            log.info("Đã dọn dẹp {} bản ghi chặn IP đã hết hạn", deletedIpBlocks);

            // 4. Dọn dẹp nhật ký đăng nhập login_attempts cũ hơn 90 ngày
            Instant attemptsRetention = now.minus(Duration.ofDays(90));
            int deletedAttempts = loginAttemptRepository.deleteOlderThan(attemptsRetention);
            log.info("Đã dọn dẹp {} bản ghi login_attempts cũ hơn 90 ngày", deletedAttempts);

            // 5. Dọn dẹp nhật ký audit_logs cũ hơn 180 ngày
            Instant auditRetention = now.minus(Duration.ofDays(180));
            int deletedAuditLogs = auditLogRepository.deleteOlderThan(auditRetention);
            log.info("Đã dọn dẹp {} bản ghi audit_logs cũ hơn 180 ngày", deletedAuditLogs);

            log.info("Hoàn tất job định kỳ dọn dẹp dữ liệu xác thực");
        } catch (Exception e) {
            log.error("Lỗi trong quá trình dọn dẹp dữ liệu xác thực: {}", e.getMessage(), e);
        }
    }
}
