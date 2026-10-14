package com.thupo.managerShop.auth.service;

import com.thupo.managerShop.auth.entity.LoginAttempt;
import com.thupo.managerShop.auth.repository.LoginAttemptRepository;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thu thập và ghi bảng login_attempts theo lô (batch) bất đồng bộ.
 * Tránh ghi trực tiếp MySQL trên mỗi request để bảo vệ throughput và connection pool khi cao điểm.
 */
@Component
public class LoginAttemptRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptRecorder.class);
    private static final int BATCH_SIZE = 100;

    private final LoginAttemptRepository repository;
    private final ConcurrentLinkedQueue<LoginAttempt> queue = new ConcurrentLinkedQueue<>();

    public LoginAttemptRecorder(LoginAttemptRepository repository) {
        this.repository = repository;
    }

    /**
     * Đưa nỗ lực đăng nhập vào hàng đợi tạm trong RAM.
     */
    public void record(String username, String ip, boolean success, String failureReason, String userAgent) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsernameAttempted(username);
        attempt.setIpAddress(ip);
        attempt.setSuccess(success);
        attempt.setFailureReason(failureReason);
        attempt.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        attempt.setCreatedAt(Instant.now());

        queue.add(attempt);

        if (queue.size() >= BATCH_SIZE) {
            flushBatch();
        }
    }

    /**
     * Chạy định kỳ mỗi 3 giây để đẩy các bản ghi còn tồn trong RAM xuống MySQL.
     */
    @Scheduled(fixedDelay = 3000)
    public void scheduledFlush() {
        if (!queue.isEmpty()) {
            flushBatch();
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("Đang flush các bản ghi login_attempts còn lại trước khi dừng server...");
        flushBatch();
    }

    private synchronized void flushBatch() {
        if (queue.isEmpty()) {
            return;
        }

        List<LoginAttempt> batch = new ArrayList<>(BATCH_SIZE);
        LoginAttempt item;
        while ((item = queue.poll()) != null && batch.size() < BATCH_SIZE) {
            batch.add(item);
        }

        if (!batch.isEmpty()) {
            try {
                repository.saveAll(batch);
            } catch (Exception e) {
                log.error("Lỗi khi ghi batch {} bản ghi login_attempts: {}", batch.size(), e.getMessage());
            }
        }
    }
}
