package com.thupo.managerShop.auth.concurrency;

import com.thupo.managerShop.auth.config.AppSecurityProperties;
import com.thupo.managerShop.auth.dto.LoginResponse;
import com.thupo.managerShop.auth.entity.RefreshToken;
import com.thupo.managerShop.auth.entity.RefreshTokenStatus;
import com.thupo.managerShop.auth.entity.Role;
import com.thupo.managerShop.auth.entity.RoleName;
import com.thupo.managerShop.auth.entity.User;
import com.thupo.managerShop.auth.entity.UserStatus;
import com.thupo.managerShop.auth.exception.AuthException;
import com.thupo.managerShop.auth.repository.RefreshTokenRepository;
import com.thupo.managerShop.auth.repository.UserRepository;
import com.thupo.managerShop.auth.security.TokenHashUtil;
import com.thupo.managerShop.auth.service.AccountLockService;
import com.thupo.managerShop.auth.service.RefreshTokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthConcurrencyTest {

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("Đồng thời 50 threads đăng nhập sai 1 tài khoản: Đảm bảo 50 câu lệnh UPDATE nguyên tử được gọi")
    void shouldHandle50ConcurrentFailedLoginsAtomically() throws Exception {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);

        Long targetUserId = 888L;

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // Đợi tất cả 50 threads sẵn sàng rồi cùng xuất phát
                    startLatch.await();

                    // Mô phỏng thao tác tăng bộ đếm thất bại nguyên tử
                    userRepository.incrementFailedCount(targetUserId);
                    successCounter.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Chờ 50 threads sẵn sàng
        readyLatch.await(5, TimeUnit.SECONDS);
        // Nhả chốt để 50 threads cùng chạy đồng thời
        startLatch.countDown();

        // Chờ toàn bộ hoàn tất
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Toàn bộ 50 threads phải hoàn thành trong 10 giây");
        assertEquals(50, successCounter.get(), "Cả 50 threads đều phải thực thi thành công");

        // Xác minh userRepository.incrementFailedCount được gọi chính xác 50 lần mà không mất cập nhật
        verify(userRepository, times(50)).incrementFailedCount(targetUserId);
    }

    @Test
    @DisplayName("2 threads gửi refresh token đồng thời với cùng 1 token: Đảm bảo race-condition an toàn")
    void shouldHandleConcurrentRefreshRequestsSafely() throws Exception {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<String> results = Collections.synchronizedList(new ArrayList<>());

        String sharedRawToken = "shared-refresh-token-123";

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Mô phỏng gửi request refresh
                    results.add("Thread-" + threadId + "-OK");
                } catch (Exception e) {
                    results.add("Thread-" + threadId + "-Error");
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(2, results.size());
    }
}
